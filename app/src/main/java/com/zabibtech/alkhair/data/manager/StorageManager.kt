package com.zabibtech.alkhair.data.manager

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageManager @Inject constructor() {

    private val storageRef = FirebaseStorage.getInstance().reference

    /**
     * Upload a file to Firebase Storage
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

            val ref = storageRef.child("$folder/$name")

            ref.putFile(fileUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()

            Result.success(downloadUrl)
        } catch (e: Exception) {
            Log.e("StorageManager", "Upload failed", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a file using its Firebase Storage URL
     */
    suspend fun deleteFile(storageUrl: String): Result<Unit> {
        return try {
            val ref = FirebaseStorage.getInstance()
                .getReferenceFromUrl(storageUrl)

            ref.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("StorageManager", "Delete failed", e)
            Result.failure(e)
        }
    }

    /**
     * Replace existing file with a new one
     * - Upload new file first
     * - Delete old file ONLY if upload succeeds
     */
    suspend fun replaceFile(
        newFileUri: Uri,
        oldFileUrl: String? = null,
        folder: String = "uploads",
        fileName: String? = null
    ): Result<String> {
        return try {
            // 1️⃣ Upload new file first
            val uploadResult = uploadFile(newFileUri, folder, fileName)
            if (uploadResult.isFailure) {
                return uploadResult
            }

            val newUrl = uploadResult.getOrThrow()

            // 2️⃣ Delete old file (best-effort)
            oldFileUrl?.let {
                deleteFile(it)
                    .onFailure { e ->
                        Log.w(
                            "StorageManager",
                            "Old file delete failed (ignored): $it",
                            e
                        )
                    }
            }

            Result.success(newUrl)
        } catch (e: Exception) {
            Log.e("StorageManager", "Replace failed", e)
            Result.failure(e)
        }
    }
}
