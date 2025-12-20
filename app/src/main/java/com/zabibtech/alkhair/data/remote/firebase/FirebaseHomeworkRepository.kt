package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.utils.FirebaseRefs.homeworkRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseHomeworkRepository @Inject constructor() {

    suspend fun createHomework(homework: Homework): Result<Homework> {
        return try {
            val homeworkId = homework.id.ifEmpty { homeworkRef.push().key!! }
            val newHomework = homework.copy(id = homeworkId, updatedAt = System.currentTimeMillis())
            homeworkRef.child(homeworkId).setValue(newHomework).await()
            Result.success(newHomework)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error creating homework", e)
            Result.failure(e)
        }
    }

    suspend fun getHomeworkById(homeworkId: String): Result<Homework> {
        return try {
            val snapshot = homeworkRef.child(homeworkId).get().await()
            val homework = snapshot.getValue(Homework::class.java)
            if (homework != null) {
                Result.success(homework)
            } else {
                Result.failure(NoSuchElementException("Homework with ID $homeworkId not found."))
            }
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error getting homework with ID $homeworkId", e)
            Result.failure(e)
        }
    }

    suspend fun getAllHomework(): Result<List<Homework>> {
        return try {
            val snapshot = homeworkRef.get().await()
            val homeworkList = snapshot.children.mapNotNull {
                it.getValue(Homework::class.java)
            }
            Result.success(homeworkList.sortedByDescending { it.updatedAt }) // Sort by updatedAt
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error getting all homework", e)
            Result.failure(e)
        }
    }

    suspend fun getHomeworkByClassName(className: String): Result<List<Homework>> {
        return try {
            val query = homeworkRef.orderByChild("className").equalTo(className)
            val snapshot = query.get().await()
            val homeworkList = snapshot.children.mapNotNull {
                it.getValue(Homework::class.java)
            }
            Result.success(homeworkList.sortedByDescending { it.updatedAt }) // Sort by updatedAt
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error getting homework for class $className", e)
            Result.failure(e)
        }
    }

    suspend fun getHomeworkByTeacherId(teacherId: String): Result<List<Homework>> {
        return try {
            val query = homeworkRef.orderByChild("teacherId").equalTo(teacherId)
            val snapshot = query.get().await()
            val homeworkList = snapshot.children.mapNotNull {
                it.getValue(Homework::class.java)
            }
            Result.success(homeworkList.sortedByDescending { it.updatedAt }) // Sort by updatedAt
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error getting homework for teacher $teacherId", e)
            Result.failure(e)
        }
    }

    suspend fun updateHomework(homeworkId: String, updatedData: Map<String, Any>): Result<Unit> {
        return try {
            val dataToUpdate = updatedData.toMutableMap()
            dataToUpdate["updatedAt"] = System.currentTimeMillis()
            homeworkRef.child(homeworkId).updateChildren(dataToUpdate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error updating homework with ID $homeworkId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteHomework(homeworkId: String): Result<Unit> {
        return try {
            homeworkRef.child(homeworkId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error deleting homework with ID $homeworkId", e)
            Result.failure(e)
        }
    }

    suspend fun getHomeworkUpdatedAfter(timestamp: Long): Result<List<Homework>> {
        return try {
            val snapshot = homeworkRef.orderByChild("updatedAt").startAt(timestamp.toDouble()).get().await()
            val homeworkList = snapshot.children.mapNotNull { it.getValue(Homework::class.java) }
            Result.success(homeworkList)
        } catch (e: Exception) {
            Log.e("FirebaseHomeworkRepo", "Error getting updated homework", e)
            Result.failure(e)
        }
    }
}