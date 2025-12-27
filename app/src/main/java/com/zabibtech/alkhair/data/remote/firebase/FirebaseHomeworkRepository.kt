package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.utils.FirebaseRefs.homeworkRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseHomeworkRepository @Inject constructor() {

    /**
     * ✅ CREATE (With Composite Key)
     * Saves homework and generates "class_sync_key" for optimized syncing.
     */
    suspend fun createHomework(homework: Homework): Result<Homework> {
        return try {
            val homeworkId = homework.id.ifEmpty { homeworkRef.push().key!! }
            val currentTime = System.currentTimeMillis()

            // 1. Final Object
            val newHomework = homework.copy(
                id = homeworkId,
                updatedAt = currentTime
            )

            // 2. Convert to Map to inject "class_sync_key"
            val homeworkMap = mapOf(
                "id" to newHomework.id,
                "className" to newHomework.className,
                "division" to newHomework.division,
                "shift" to newHomework.shift,
                "subject" to newHomework.subject,
                "title" to newHomework.title,
                "description" to newHomework.description,
                "teacherId" to newHomework.teacherId,
                "attachmentUrl" to (newHomework.attachmentUrl ?: ""),
                "updatedAt" to newHomework.updatedAt,

                // 🔥 COMPOSITE KEY: ClassName + Timestamp
                // Example: "Class_10_1766500000"
                "class_sync_key" to "${newHomework.className}_$currentTime"
            )

            homeworkRef.child(homeworkId).setValue(homeworkMap).await()
            Result.success(newHomework)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error creating homework", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ UPDATE (Maintain Composite Key)
     * Updates fields and regenerates the sync key to trigger updates on student devices.
     */
    suspend fun updateHomework(homeworkId: String, updatedData: Map<String, Any>): Result<Unit> {
        return try {
            val dataToUpdate = updatedData.toMutableMap()
            val currentTime = System.currentTimeMillis()

            // 1. Update Timestamp
            dataToUpdate["updatedAt"] = currentTime

            // 2. Update Composite Key
            // We assume 'className' is present in the map as passed from RepoManager
            if (dataToUpdate.containsKey("className")) {
                val className = dataToUpdate["className"] as String
                dataToUpdate["class_sync_key"] = "${className}_$currentTime"
            } else {
                // Fallback: If className wasn't passed (rare), we can't update the key correctly
                // without fetching the old data. But based on your RepoManager code, it IS passed.
                Log.w("FirebaseHomeworkRepo", "className missing in update, sync key might be stale")
            }

            homeworkRef.child(homeworkId).updateChildren(dataToUpdate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error updating homework with ID $homeworkId", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ STUDENT SYNC (Targeted Class Sync)
     * Fetches only homework for a specific class updated after the timestamp.
     */
    suspend fun getHomeworkForClassUpdatedAfter(className: String, timestamp: Long): Result<List<Homework>> {
        return try {
            val startKey = "${className}_${timestamp + 1}"
            val endKey = "${className}_9999999999999"

            val snapshot = homeworkRef
                .orderByChild("class_sync_key") // ✅ Index Required in Firebase Rules
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val list = snapshot.children.mapNotNull { it.getValue(Homework::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error fetching class homework", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ ADMIN SYNC (Global)
     */
    suspend fun getHomeworkUpdatedAfter(timestamp: Long): Result<List<Homework>> {
        return try {
            val snapshot = homeworkRef
                .orderByChild("updatedAt")
                .startAt((timestamp + 1).toDouble())
                .get()
                .await()
            val list = snapshot.children.mapNotNull { it.getValue(Homework::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error getting updated homework", e)
            Result.failure(e)
        }
    }

    // --- Standard Methods ---

    suspend fun getHomeworkById(homeworkId: String): Result<Homework> {
        return try {
            val snapshot = homeworkRef.child(homeworkId).get().await()
            val homework = snapshot.getValue(Homework::class.java)
            if (homework != null) {
                Result.success(homework)
            } else {
                Result.failure(NoSuchElementException("Homework not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHomework(homeworkId: String): Result<Unit> {
        return try {
            homeworkRef.child(homeworkId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error deleting homework", e)
            Result.failure(e)
        }
    }

    // Legacy support if needed
    suspend fun getAllHomework(): Result<List<Homework>> {
        return try {
            val snapshot = homeworkRef.get().await()
            val list = snapshot.children.mapNotNull { it.getValue(Homework::class.java) }
            Result.success(list)
        } catch (e: Exception) { Result.failure(e) }
    }
}