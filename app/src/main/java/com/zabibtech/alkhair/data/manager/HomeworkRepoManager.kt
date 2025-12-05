package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalHomeworkRepository
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.data.remote.firebase.FirebaseHomeworkRepository
import com.zabibtech.alkhair.utils.StaleDetector
import kotlinx.coroutines.flow.first
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
        val localData = try {
            localHomeworkRepository.getAllHomework().first()
        } catch (e: Exception) {
            Log.w("HomeworkRepoManager", "Could not get all local homework data", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseHomeworkRepository.getAllHomework()
        return remoteResult.fold(
            onSuccess = { remoteHomeworkList ->
                try {
                    localHomeworkRepository.clearAll()
                    val updated = remoteHomeworkList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localHomeworkRepository.insertHomeworkList(updated)
                } catch (e: Exception) {
                    Log.e("HomeworkRepoManager", "Failed to refresh all homework cache from remote", e)
                }
                Result.success(remoteHomeworkList)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getHomeworkByTeacherId(teacherId: String): Result<List<Homework>> {
        val localData = try {
            localHomeworkRepository.getAllHomework().first().filter { it.teacherId == teacherId }
        } catch (e: Exception) {
            Log.w("HomeworkRepoManager", "Could not get local homework by teacher ID: $teacherId", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseHomeworkRepository.getHomeworkByTeacherId(teacherId)
        return remoteResult.fold(
            onSuccess = { remoteHomeworkList ->
                try {
                    val updated = remoteHomeworkList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localHomeworkRepository.insertHomeworkList(updated)
                } catch (e: Exception) {
                    Log.e("HomeworkRepoManager", "Failed to refresh homework for teacher $teacherId cache from remote", e)
                }
                Result.success(remoteHomeworkList)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getHomeworkByClass(className: String, division: String): Result<List<Homework>> {
        val localData = try {
            localHomeworkRepository.getHomeworkByClass(className, division).first()
        } catch (e: Exception) {
            Log.w("HomeworkRepoManager", "Could not get local homework by class: $className, $division", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseHomeworkRepository.getHomeworkByClassName(className)
        return remoteResult.fold(
            onSuccess = { remoteHomeworkList ->
                val filteredList = remoteHomeworkList.filter { it.division == division }
                try {
                    val updated = filteredList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localHomeworkRepository.insertHomeworkList(updated)
                } catch (e: Exception) {
                    Log.e("HomeworkRepoManager", "Failed to refresh homework for class $className/$division cache from remote", e)
                }
                Result.success(filteredList)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun updateHomework(homework: Homework): Result<Unit> {
        val firebaseUpdateMap: Map<String, Any?> = mapOf(
            "className" to homework.className,
            "division" to homework.division,
            "shift" to homework.shift,
            "subject" to homework.subject,
            "title" to homework.title,
            "description" to homework.description,
            "date" to homework.date,
            "teacherId" to homework.teacherId,
            "attachmentUrl" to homework.attachmentUrl
        )
        val result = firebaseHomeworkRepository.updateHomework(homework.id, firebaseUpdateMap.filterValues { it != null } as Map<String, Any>)
        result.onSuccess { _ ->
            try {
                localHomeworkRepository.insertHomework(homework.copy(updatedAt = System.currentTimeMillis()))
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
            } catch (e: Exception) {
                Log.e("HomeworkRepoManager", "Failed to delete homework from local cache: $homeworkId", e)
            }
        }
        return result
    }
}
