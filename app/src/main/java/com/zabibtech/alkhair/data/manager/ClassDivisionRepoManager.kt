package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalClassRepository
import com.zabibtech.alkhair.data.local.local_repos.LocalDivisionRepository
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseClassRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseDivisionRepository
import kotlinx.coroutines.flow.Flow
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

    fun getAllDivisions(): Flow<List<DivisionModel>> = localDivisionRepo.getAllDivisions()

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
        val result = firebaseDivisionRepo.updateDivision(division)
        result.onSuccess { _ ->
            try {
                localDivisionRepo.insertDivision(division.copy(updatedAt = System.currentTimeMillis()))
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
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to delete division from cache", e)
            }
        }
        return result
    }

    suspend fun refreshDivisions() {
        firebaseDivisionRepo.getAllDivisions().onSuccess { divisions ->
            try {
                val updated = divisions.map { it.copy(updatedAt = System.currentTimeMillis()) }
                localDivisionRepo.insertDivisions(updated)
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to refresh divisions cache", e)
            }
        }
    }

    // =============================
    // Class Logic
    // =============================

    fun getAllClasses(): Flow<List<ClassModel>> = localClassRepo.getAllClasses()

    suspend fun addClass(classModel: ClassModel): Result<ClassModel> {
        // Step 1: Check if the division exists.
        val divisionExistsResult = firebaseDivisionRepo.doesDivisionExist(classModel.division)

        // Handle failure in checking
        if (divisionExistsResult.isFailure) {
            return Result.failure(divisionExistsResult.exceptionOrNull() ?: Exception("Failed to check division existence"))
        }

        // If division does not exist, create it using the manager's own `addDivision` function.
        if (divisionExistsResult.getOrNull() == false) {
            val newDivision = DivisionModel(name = classModel.division)
            val addDivisionResult = addDivision(newDivision) // This handles remote + local caching
            if (addDivisionResult.isFailure) {
                return Result.failure(addDivisionResult.exceptionOrNull() ?: Exception("Failed to create new division"))
            }
        }

        // Step 2: Now that division is guaranteed to exist, add the class.
        val classResult = firebaseClassRepo.addClass(classModel)

        // Step 3: On success, cache the class locally.
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
        val result = firebaseClassRepo.updateClass(classModel)
        result.onSuccess { _ ->
            try {
                localClassRepo.insertClass(classModel.copy(updatedAt = System.currentTimeMillis()))
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
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to delete class from cache", e)
            }
        }
        return result
    }

    suspend fun refreshClasses() {
        firebaseClassRepo.getAllClasses().onSuccess { classes ->
            try {
                val updated = classes.map { it.copy(updatedAt = System.currentTimeMillis()) }
                localClassRepo.insertClasses(updated)
            } catch (e: Exception) {
                Log.e("ClassDivisionManager", "Failed to refresh classes cache", e)
            }
        }
    }
}