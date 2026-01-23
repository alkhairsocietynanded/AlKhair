package com.zabibtech.alkhair.data.manager

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
        private const val BUCKET_NAME = "homework" // Ensure this bucket is public in Supabase
    }

    /**
     * Upload a file to Supabase Storage
     */
    suspend fun uploadFile(
        fileUri: Uri,
        folder: String = "uploads",
        fileName: String? = null
    ): Result<String> {
        return try {
            val name = fileName
                ?: fileUri.lastPathSegment
                ?: "file_${System.currentTimeMillis()}"
            
            val path = "$folder/$name"
            val bucket = supabaseClient.storage.from(BUCKET_NAME)

            // Read bytes from URI
            val bytes = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: return Result.failure(Exception("Could not read file from URI"))

            // Upload (upsert=true to overwrite if exists)
            bucket.upload(path, bytes) {
                upsert = true
            }

            // Get Public URL
            val publicUrl = bucket.publicUrl(path)
            Log.d(TAG, "Upload success: $publicUrl")

            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a file using its Supabase Storage URL
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
