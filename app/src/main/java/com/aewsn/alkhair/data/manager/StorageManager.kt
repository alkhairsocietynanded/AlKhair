package com.aewsn.alkhair.data.manager

import android.net.Uri
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    companion object {
        private const val TAG = "StorageManager"
        private const val BUCKET_NAME = "alkhair_assets" // Ensure this bucket is public in Supabase

        /**
         * Copy a temporary content URI to a internal cache file so that 
         * background repositories can read it without permission issues.
         */
        fun stageUriToCache(callerContext: android.content.Context, uri: Uri): Uri? {
            return try {
                val cacheFolder = java.io.File(callerContext.cacheDir, "staged_uploads")
                if (!cacheFolder.exists()) cacheFolder.mkdirs()
                
                val fileName = "staged_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "file"}"
                val tempFile = java.io.File(cacheFolder, fileName)
                
                callerContext.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(tempFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stage URI: ${e.message}", e)
                null
            }
        }
    }

    suspend fun processMediaUriToBytes(fileUri: Uri, originalMimeType: String? = null): Pair<ByteArray, String> {
        var mimeType = originalMimeType ?: context.contentResolver.getType(fileUri)
        
        if (mimeType == null || mimeType == "application/octet-stream") {
             val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(fileUri.toString())
             if (!extension.isNullOrEmpty()) {
                 mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
             }
        }
        val safeMimeType = mimeType ?: "application/octet-stream"
        
        var finalBytes: ByteArray? = null
        var finalMimeType = safeMimeType

        if (safeMimeType.startsWith("image/") && safeMimeType != "image/gif") {
            com.aewsn.alkhair.utils.ImageCompressor.compressImageFromUri(context, fileUri)?.let { compressedBytes ->
                finalBytes = compressedBytes
                finalMimeType = "image/webp"
            }
        }
        
        if (finalBytes == null) {
             finalBytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                 ?: throw Exception("Could not read file from URI")
        }

        // Cleanup staged file if any
        if (fileUri.scheme == "file" && fileUri.path?.contains("staged_uploads") == true) {
             try { java.io.File(fileUri.path!!).delete() } catch (e: Exception) {}
        }
        
        return Pair(finalBytes!!, finalMimeType)
    }

    suspend fun uploadFile(
        fileUri: Uri,
        folder: String = "uploads",
        fileName: String? = null
    ): Result<String> {
        return try {
            val (bytes, finalMimeType) = processMediaUriToBytes(fileUri)
            
            var finalName = fileName ?: fileUri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
            if (finalMimeType == "image/webp") {
                val lastDotIndex = finalName.lastIndexOf('.')
                if (lastDotIndex != -1) {
                    finalName = finalName.substring(0, lastDotIndex) + ".webp"
                } else {
                    finalName += ".webp"
                }
            }

            uploadBytes(bytes = bytes, folder = folder, fileName = finalName, contentType = finalMimeType)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Upload raw bytes to Supabase Storage.
     * Prefer this when bytes are already read in Activity context (content picker URIs).
     */
    suspend fun uploadBytes(
        bytes: ByteArray,
        folder: String = "uploads",
        fileName: String,
        contentType: String = "application/octet-stream"
    ): Result<String> {
        return try {
            val path = "$folder/$fileName"
            val bucket = supabaseClient.storage.from(BUCKET_NAME)
            Log.d(TAG, "Uploading ${bytes.size} bytes to $path (type=$contentType)")

            bucket.upload(path, bytes) {
                upsert = true
                this.contentType = io.ktor.http.ContentType.parse(contentType)
            }

            val publicUrl = bucket.publicUrl(path)
            Log.d(TAG, "Upload success: $publicUrl")
            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Upload bytes failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Cache raw bytes locally so the sender doesn't have to re-download
     * their own files. Returns the absolute local path.
     */
    fun saveBytesToCache(bytes: ByteArray, fileName: String): String? {
        return try {
            val cacheFolder = java.io.File(context.cacheDir, "chat_media")
            if (!cacheFolder.exists()) cacheFolder.mkdirs()
            
            val destFile = java.io.File(cacheFolder, fileName)
            destFile.writeBytes(bytes)
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bytes to cache: ${e.message}", e)
            null
        }
    }


    /**
     * Check if a media file (identified by its remote URL) is already cached locally.
     *
     * Called during sync to auto-populate `local_uri` in Room without re-downloading.
     * This handles DB wipe (destructive migration) where cache files survive but Room loses local_uri.
     *
     * Returns absolute path string if file exists and is non-empty, null otherwise.
     */
    fun findCachedFile(mediaUrl: String): String? {
        val rawSegment = mediaUrl.substringAfterLast("/")
        val safeFileName = if (rawSegment.isNotBlank()) rawSegment else return null
        val cacheFile = java.io.File(context.cacheDir, "chat_media/$safeFileName")
        return if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "Cache hit for $safeFileName")
            cacheFile.absolutePath
        } else null
    }

    /**
     * Download a publicly accessible file from Supabase Storage by URL.
     * Saves the file to `cacheDir/chat_media/fileName`.
     * Returns Result<String> — absolute path of the saved file on success.
     */
    suspend fun downloadPublicFile(storageUrl: String, fileName: String): Result<String> {
        return try {
            // Ensure the chat_media folder exists in app's cache directory
            val cacheFolder = java.io.File(context.cacheDir, "chat_media")
            if (!cacheFolder.exists()) cacheFolder.mkdirs()

            val destFile = java.io.File(cacheFolder, fileName)

            // If already downloaded (cached), return existing path immediately
            if (destFile.exists() && destFile.length() > 0) {
                Log.d(TAG, "File already cached: ${destFile.absolutePath}")
                return Result.success(destFile.absolutePath)
            }

            // Extract storage path from the public URL to use the Supabase SDK download
            // URL format: .../storage/v1/object/public/<bucket>/<path>
            val bucketPrefix = "/$BUCKET_NAME/"
            val storagePath = if (storageUrl.contains(bucketPrefix)) {
                storageUrl.substringAfter(bucketPrefix)
            } else {
                // Fallback: download directly via HTTP without SDK
                Log.w(TAG, "Unexpected URL format, falling back to HTTP: $storageUrl")
                val bytes = java.net.URL(storageUrl).readBytes()
                destFile.writeBytes(bytes)
                Log.d(TAG, "Downloaded via HTTP to: ${destFile.absolutePath}")
                return Result.success(destFile.absolutePath)
            }

            // Download bytes from Supabase Storage SDK
            val bytes = supabaseClient.storage.from(BUCKET_NAME).downloadPublic(storagePath)
            destFile.writeBytes(bytes)
            Log.d(TAG, "Downloaded to: ${destFile.absolutePath} (${bytes.size} bytes)")

            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $storageUrl: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a publicly accessible file from Supabase Storage URL
     */
    suspend fun deleteFile(storageUrl: String): Result<Unit> {
        return try {
            // Extract path from URL
            // Format: .../storage/v1/object/public/homework/uploads/filename
            val bucketPath = "$BUCKET_NAME/"
            if (!storageUrl.contains(bucketPath)) {
                 Log.w(TAG, "Invalid Supabase URL: $storageUrl")
                 return Result.failure(Exception("Invalid URL format"))
            }

            val path = storageUrl.substringAfter(bucketPath)
            
            supabaseClient.storage.from(BUCKET_NAME).delete(path)
            Log.d(TAG, "Deleted file: $path")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed", e)
            // Retrieve actual error message if possible, but failure here shouldn't block app flow usually
            Result.failure(e)
        }
    }

    /**
     * Replace existing file with a new one
     */
    suspend fun replaceFile(
        newFileUri: Uri,
        oldFileUrl: String? = null,
        folder: String = "uploads",
        fileName: String? = null
    ): Result<String> {
        // 1️⃣ Upload new file first
        val uploadResult = uploadFile(newFileUri, folder, fileName)
        if (uploadResult.isFailure) return uploadResult

        // 2️⃣ Delete old file (best-effort)
        if (!oldFileUrl.isNullOrBlank()) {
             deleteFile(oldFileUrl).onFailure {
                 Log.w(TAG, "Old file delete failed: $it")
             }
        }
        
        return uploadResult
    }
}
