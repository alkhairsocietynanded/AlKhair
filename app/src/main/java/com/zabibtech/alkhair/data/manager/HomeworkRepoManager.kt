package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalHomeworkRepository
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.data.remote.firebase.FirebaseHomeworkRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeworkRepoManager @Inject constructor(
    private val localHomeworkRepository: LocalHomeworkRepository,
    private val firebaseHomeworkRepository: FirebaseHomeworkRepository
) {

    suspend fun createHomework(homework: Homework): Result<Homework> {
        val result = firebaseHomeworkRepository.createHomework(homework)
        result.onSuccess { newHomework ->
            try {
                localHomeworkRepository.insertHomework(newHomework.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("HomeworkRepoManager", "Failed to cache created homework locally", e)
            }
        }
        return result
    }

    suspend fun getAllHomework(): Result<List<Homework>> {
        return try {
            val localData = localHomeworkRepository.getAllHomework().first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("HomeworkRepoManager", "Could not get all local homework data", e)
            Result.failure(e)
        }
    }

    suspend fun syncHomework(lastSync: Long) {
        firebaseHomeworkRepository.getHomeworkUpdatedAfter(lastSync).onSuccess { homework ->
            if (homework.isNotEmpty()) {
                try {
                    val updatedList =
                        homework.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localHomeworkRepository.insertHomeworkList(updatedList)
                } catch (e: Exception) {
                    Log.e("HomeworkRepoManager", "Failed to cache synced homework", e)
                }
            }
        }
    }

    suspend fun getHomeworkByTeacherId(teacherId: String): Result<List<Homework>> {
        return try {
            val localData = localHomeworkRepository.getAllHomework().first().filter { it.teacherId == teacherId }
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("HomeworkRepoManager", "Could not get local homework by teacher ID: $teacherId", e)
            Result.failure(e)
        }
    }

    suspend fun getHomeworkByClass(className: String, division: String): Result<List<Homework>> {
        return try {
            val localData = localHomeworkRepository.getHomeworkByClass(className, division).first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("HomeworkRepoManager", "Could not get local homework by class: $className, $division", e)
            Result.failure(e)
        }
    }

    suspend fun updateHomework(homework: Homework): Result<Unit> {
        val homeworkToUpdate = homework.copy(updatedAt = System.currentTimeMillis())
        val firebaseUpdateMap: Map<String, Any?> = mapOf(
            "className" to homeworkToUpdate.className,
            "division" to homeworkToUpdate.division,
            "shift" to homeworkToUpdate.shift,
            "subject" to homeworkToUpdate.subject,
            "title" to homeworkToUpdate.title,
            "description" to homeworkToUpdate.description,
            "date" to homeworkToUpdate.date,
            "teacherId" to homeworkToUpdate.teacherId,
            "attachmentUrl" to homeworkToUpdate.attachmentUrl,
            "updatedAt" to homeworkToUpdate.updatedAt
        )
        val result = firebaseHomeworkRepository.updateHomework(
            homework.id,
            firebaseUpdateMap.filterValues { it != null } as Map<String, Any>)
        result.onSuccess { _ ->
            try {
                localHomeworkRepository.insertHomework(homeworkToUpdate)
            } catch (e: Exception) {
                Log.e("HomeworkRepoManager", "Failed to cache updated homework locally", e)
            }
        }
        return result
    }

    suspend fun deleteHomework(homeworkId: String): Result<Unit> {
        val result = firebaseHomeworkRepository.deleteHomework(homeworkId)
        result.onSuccess { _ ->
            try {
                localHomeworkRepository.deleteHomeworkById(homeworkId)

                val deletedRecord = DeletedRecord(
                    id = homeworkId,
                    type = "homework",
                    timestamp = System.currentTimeMillis()
                )
                FirebaseRefs.deletedRecordsRef.child(homeworkId).setValue(deletedRecord).await()

            } catch (e: Exception) {
                Log.e("HomeworkRepoManager", "Failed to delete homework from local cache: $homeworkId", e)
            }
        }
        return result
    }

    suspend fun deleteHomeworkLocally(id: String) {
        try {
            localHomeworkRepository.deleteHomeworkById(id)
        } catch (e: Exception) {
            Log.e("HomeworkRepoManager", "Failed to delete local Homework: $id", e)
        }
    }
}
