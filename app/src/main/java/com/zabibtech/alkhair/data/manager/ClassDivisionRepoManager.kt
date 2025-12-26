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
import kotlinx.coroutines.flow.Flow
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

    /* ============================================================
       📦 READ — SSOT (Flow from Room)
       ============================================================ */

    fun observeClasses(): Flow<List<ClassModel>> =
        localClassRepo.getAllClasses() // Ensure DAO returns Flow

    fun observeDivisions(): Flow<List<DivisionModel>> =
        localDivisionRepo.getAllDivisions() // Ensure DAO returns Flow

    // ✅ One-Shot Getters (for ViewModel initialization / checks)
    suspend fun getAllClassesSnapshot(): Result<List<ClassModel>> {
        return try {
            val list = localClassRepo.getAllClasses().first()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllDivisionsSnapshot(): Result<List<DivisionModel>> {
        return try {
            val list = localDivisionRepo.getAllDivisions().first()
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /* ============================================================
       ✍️ WRITE — (Remote First -> Then Local)
       ============================================================ */

    // --- DIVISIONS ---
    suspend fun addDivision(division: DivisionModel): Result<DivisionModel> {
        return firebaseDivisionRepo.addDivision(division).onSuccess { newDivision ->
            localDivisionRepo.insertDivision(newDivision)
        }
    }

    suspend fun updateDivision(division: DivisionModel): Result<Unit> {
        val divisionToUpdate = division.copy(updatedAt = System.currentTimeMillis())
        return firebaseDivisionRepo.updateDivision(divisionToUpdate).onSuccess {
            localDivisionRepo.insertDivision(divisionToUpdate)
        }
    }

    suspend fun deleteDivision(divisionId: String): Result<Unit> {
        return firebaseDivisionRepo.deleteDivision(divisionId).onSuccess {
            localDivisionRepo.deleteDivision(divisionId)
            createTombstone(divisionId, "division")
        }
    }

    // --- CLASSES ---
    suspend fun addClass(classModel: ClassModel): Result<ClassModel> {
        // Ensure division exists remotely
        val divExists = firebaseDivisionRepo.doesDivisionExist(classModel.division).getOrElse { false }
        if (!divExists) {
            addDivision(DivisionModel(name = classModel.division))
        }

        return firebaseClassRepo.addClass(classModel).onSuccess { newClass ->
            localClassRepo.insertClass(newClass)
        }
    }

    suspend fun updateClass(classModel: ClassModel): Result<Unit> {
        val classToUpdate = classModel.copy(updatedAt = System.currentTimeMillis())
        return firebaseClassRepo.updateClass(classToUpdate).onSuccess {
            localClassRepo.insertClass(classToUpdate)
        }
    }

    suspend fun deleteClass(classId: String): Result<Unit> {
        return firebaseClassRepo.deleteClass(classId).onSuccess {
            localClassRepo.deleteClass(classId)
            createTombstone(classId, "class")
        }
    }

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */

    suspend fun syncDivisions(lastSync: Long): Result<Unit> {
        return firebaseDivisionRepo.getDivisionsUpdatedAfter(lastSync).onSuccess { list ->
            if (list.isNotEmpty()) {
                val updated = list.map { it.copy(updatedAt = System.currentTimeMillis()) }
                localDivisionRepo.insertDivisions(updated)
            }
        }.map { }
    }

    suspend fun syncClasses(lastSync: Long): Result<Unit> {
        return firebaseClassRepo.getClassesUpdatedAfter(lastSync).onSuccess { list ->
            if (list.isNotEmpty()) {
                val updated = list.map { it.copy(updatedAt = System.currentTimeMillis()) }
                localClassRepo.insertClasses(updated)
            }
        }.map { }
    }

    // Helper for Deletions
    private suspend fun createTombstone(id: String, type: String) {
        try {
            val record = DeletedRecord(id = id, type = type, timestamp = System.currentTimeMillis())
            FirebaseRefs.deletedRecordsRef.child(id).setValue(record).await()
        } catch (e: Exception) {
            Log.e("ClassDivisionRepo", "Failed to create tombstone", e)
        }
    }

    suspend fun deleteClassLocally(id: String) = localClassRepo.deleteClass(id)
    suspend fun deleteDivisionLocally(id: String) = localDivisionRepo.deleteDivision(id)
}