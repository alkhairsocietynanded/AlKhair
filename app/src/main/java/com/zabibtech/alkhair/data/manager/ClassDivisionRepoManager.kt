package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalClassRepository
import com.zabibtech.alkhair.data.local.local_repos.LocalDivisionRepository
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseClassRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseDivisionRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassDivisionRepoManager @Inject constructor(
    private val localClassRepo: LocalClassRepository,
    private val firebaseClassRepo: FirebaseClassRepository,
    private val localDivisionRepo: LocalDivisionRepository,
    private val firebaseDivisionRepo: FirebaseDivisionRepository
) {

    // =============================
    // Division Logic
    // =============================

    suspend fun getAllDivisions(): Result<List<DivisionModel>> {
        val localData = try {
            localDivisionRepo.getAllDivisions().first()
        } catch (e: Exception) {
            Log.w("ClassDivisionRepoManager", "Could not get all local divisions", e)
            emptyList()
        }

        if (localData.isNotEmpty()) {
            return Result.success(localData)
        }

        val remoteResult = firebaseDivisionRepo.getAllDivisions()
        return remoteResult.fold(
            onSuccess = { remoteList ->
                try {
                    val updatedList = remoteList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localDivisionRepo.insertDivisions(updatedList)
                } catch (e: Exception) {
                    Log.e("ClassDivisionRepoManager", "Failed to cache initial divisions", e)
                }
                Result.success(remoteList)
            },
            onFailure = { exception ->
                Result.failure(exception)
            }
        )
    }

    suspend fun syncDivisions(lastSync: Long) {
        firebaseDivisionRepo.getDivisionsUpdatedAfter(lastSync).onSuccess { divisions ->
            if (divisions.isNotEmpty()) {
                try {
                    val updatedList = divisions.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localDivisionRepo.insertDivisions(updatedList)
                } catch (e: Exception) {
                    Log.e("ClassDivisionRepoManager", "Failed to cache synced divisions", e)
                }
            }
        }
    }

    suspend fun addDivision(division: DivisionModel): Result<DivisionModel> {
        val result = firebaseDivisionRepo.addDivision(division)
        result.onSuccess { newDivision ->
            try {
                localDivisionRepo.insertDivision(newDivision.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to cache new division", e)
            }
        }
        return result
    }

    suspend fun updateDivision(division: DivisionModel): Result<Unit> {
        val divisionToUpdate = division.copy(updatedAt = System.currentTimeMillis())
        val result = firebaseDivisionRepo.updateDivision(divisionToUpdate)
        result.onSuccess { _ ->
            try {
                localDivisionRepo.insertDivision(divisionToUpdate)
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to cache updated division", e)
            }
        }
        return result
    }

    suspend fun deleteDivision(divisionId: String): Result<Unit> {
        val result = firebaseDivisionRepo.deleteDivision(divisionId)
        result.onSuccess { _ ->
            try {
                localDivisionRepo.deleteDivision(divisionId)
                val deletedRecord = DeletedRecord(id = divisionId, type = "division", timestamp = System.currentTimeMillis())
                FirebaseRefs.deletedRecordsRef.child(divisionId).setValue(deletedRecord).await()
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to process division deletion", e)
            }
        }
        return result
    }

    suspend fun deleteDivisionLocally(id: String) {
        try {
            localDivisionRepo.deleteDivision(id)
        } catch (e: Exception) {
            Log.e("ClassDivisionManager", "Failed to delete division locally", e)
        }
    }

    // =============================
    // Class Logic
    // =============================
    suspend fun getAllClasses(): Result<List<ClassModel>> {
        val localData = try {
            localClassRepo.getAllClasses().first()
        } catch (e: Exception) {
            Log.w("ClassDivisionRepoManager", "Could not get all local classes", e)
            emptyList()
        }

        if (localData.isNotEmpty()) {
            return Result.success(localData)
        }

        val remoteResult = firebaseClassRepo.getAllClasses()
        return remoteResult.fold(
            onSuccess = { remoteList ->
                try {
                    val updatedList = remoteList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localClassRepo.insertClasses(updatedList)
                } catch (e: Exception) {
                    Log.e("ClassDivisionRepoManager", "Failed to cache initial classes", e)
                }
                Result.success(remoteList)
            },
            onFailure = { exception ->
                Result.failure(exception)
            }
        )
    }

    suspend fun syncClasses(lastSync: Long) {
        firebaseClassRepo.getClassesUpdatedAfter(lastSync).onSuccess { classes ->
            if (classes.isNotEmpty()) {
                try {
                    val updatedList = classes.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localClassRepo.insertClasses(updatedList)
                } catch (e: Exception) {
                    Log.e("ClassDivisionRepoManager", "Failed to cache synced classes", e)
                }
            }
        }
    }

    suspend fun addClass(classModel: ClassModel): Result<ClassModel> {
        val divisionExistsResult = firebaseDivisionRepo.doesDivisionExist(classModel.division)

        if (divisionExistsResult.isFailure) {
            return Result.failure(divisionExistsResult.exceptionOrNull() ?: Exception("Failed to check division existence"))
        }

        if (divisionExistsResult.getOrNull() == false) {
            val newDivision = DivisionModel(name = classModel.division)
            val addDivisionResult = addDivision(newDivision)
            if (addDivisionResult.isFailure) {
                return Result.failure(addDivisionResult.exceptionOrNull() ?: Exception("Failed to create new division"))
            }
        }

        val classResult = firebaseClassRepo.addClass(classModel)
        classResult.onSuccess { newClass ->
            try {
                localClassRepo.insertClass(newClass.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to cache new class", e)
            }
        }
        return classResult
    }

    suspend fun updateClass(classModel: ClassModel): Result<Unit> {
        val classToUpdate = classModel.copy(updatedAt = System.currentTimeMillis())
        val result = firebaseClassRepo.updateClass(classToUpdate)
        result.onSuccess { _ ->
            try {
                localClassRepo.insertClass(classToUpdate)
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to cache updated class", e)
            }
        }
        return result
    }

    suspend fun deleteClass(classId: String): Result<Unit> {
        val result = firebaseClassRepo.deleteClass(classId)
        result.onSuccess { _ ->
            try {
                localClassRepo.deleteClass(classId)
                val deletedRecord = DeletedRecord(id = classId, type = "class", timestamp = System.currentTimeMillis())
                FirebaseRefs.deletedRecordsRef.child(classId).setValue(deletedRecord).await()
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to process class deletion", e)
            }
        }
        return result
    }

    suspend fun deleteClassLocally(id: String) {
        try {
            localClassRepo.deleteClass(id)
        } catch(e: Exception) {
            Log.e("ClassDivisionManager", "Failed to delete class locally", e)
        }
    }
}
