package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalClassRepository
import com.zabibtech.alkhair.data.local.local_repos.LocalDivisionRepository
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.remote.supabase.SupabaseClassRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseDivisionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.models.PendingDeletion
import com.zabibtech.alkhair.data.worker.ClassUploadWorker
import com.zabibtech.alkhair.data.worker.DivisionUploadWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

@Singleton

class ClassDivisionRepoManager @Inject constructor(
    private val localClassRepo: LocalClassRepository,
    private val supabaseClassRepo: SupabaseClassRepository,
    private val localDivisionRepo: LocalDivisionRepository,
    private val supabaseDivisionRepo: SupabaseDivisionRepository,
    private val workManager: WorkManager,
    private val pendingDeletionDao: PendingDeletionDao
) {

    /* ============================================================
       📦 READ — SSOT (Flow from Room)
       ============================================================ */

    fun observeClasses(): Flow<List<ClassModel>> =
        localClassRepo.getAllClasses()

    fun observeDivisions(): Flow<List<DivisionModel>> =
        localDivisionRepo.getAllDivisions()

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
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    // --- DIVISIONS ---
    suspend fun addDivision(division: DivisionModel): Result<DivisionModel> {
        // Prevent duplicate division names
        val existing = localDivisionRepo.getDivisionByName(division.name)
        if (existing != null) {
            return Result.success(existing)
        }
        
        val newId = division.id.ifEmpty { java.util.UUID.randomUUID().toString() }
        val currentTime = System.currentTimeMillis()
        
        val newDivision = division.copy(
            id = newId,
            updatedAt = currentTime,
            isSynced = false
        )
        
        localDivisionRepo.insertDivision(newDivision)
        scheduleDivisionUploadWorker()
        
        return Result.success(newDivision)
    }

    suspend fun updateDivision(division: DivisionModel): Result<Unit> {
        val updatedDivision = division.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        localDivisionRepo.insertDivision(updatedDivision)
        scheduleDivisionUploadWorker()
        return Result.success(Unit)
    }

    suspend fun deleteDivision(divisionId: String): Result<Unit> {
        localDivisionRepo.deleteDivision(divisionId)
        
        val pendingDeletion = PendingDeletion(
            id = divisionId,
            type = "DIVISION",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        
        scheduleDivisionUploadWorker()
        return Result.success(Unit)
    }


    // --- CLASSES ---
    suspend fun addClass(classModel: ClassModel): Result<ClassModel> {
        Log.d("RepoManager", "addClass called for: ${classModel.className} in ${classModel.divisionName}")
        
        var existingDiv = localDivisionRepo.getDivisionByName(classModel.divisionName)
        Log.d("RepoManager", "Existing division found: ${existingDiv?.id}")

        if (existingDiv == null) {
              Log.d("RepoManager", "Division not found, creating new: ${classModel.divisionName}")
              val newDivResult = addDivision(DivisionModel(name = classModel.divisionName))
              if (newDivResult.isSuccess) {
                  existingDiv = newDivResult.getOrNull()
                  Log.d("RepoManager", "New division created: ${existingDiv?.id}")
              } else {
                  Log.e("RepoManager", "Failed to create division: ${newDivResult.exceptionOrNull()}")
              }
        }
        
        val divId = existingDiv?.id ?: return Result.failure(Exception("Failed to create or find Division"))

        val newId = classModel.id.ifEmpty { java.util.UUID.randomUUID().toString() }
        val currentTime = System.currentTimeMillis()
        
        val newClass = classModel.copy(
            id = newId,
            divisionId = divId, // ✅ Link Foreign Key
            updatedAt = currentTime,
            isSynced = false
        )
        
        localClassRepo.insertClass(newClass)
        Log.d("RepoManager", "Class inserted locally: $newId")
        scheduleClassUploadWorker()
        
        return Result.success(newClass)
    }

    suspend fun updateClass(classModel: ClassModel): Result<Unit> {
        val updatedClass = classModel.copy(
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
        localClassRepo.insertClass(updatedClass)
        scheduleClassUploadWorker()
        return Result.success(Unit)
    }

    suspend fun deleteClass(classId: String): Result<Unit> {
        localClassRepo.deleteClass(classId)
        
        val pendingDeletion = PendingDeletion(
            id = classId,
            type = "CLASS",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        
        scheduleClassUploadWorker()
        return Result.success(Unit)
    }

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */

    suspend fun syncDivisions(lastSync: Long): Result<Unit> {
        return supabaseDivisionRepo.getDivisionsUpdatedAfter(lastSync).onSuccess { list ->
            if (list.isNotEmpty()) {
                val updated = list.map { it.copy(updatedAt = System.currentTimeMillis()) }
                localDivisionRepo.insertDivisions(updated)
            }
        }.map { }
    }

    suspend fun syncClasses(lastSync: Long): Result<Unit> {
        return supabaseClassRepo.getClassesUpdatedAfter(lastSync).onSuccess { list ->
            if (list.isNotEmpty()) {
                val updated = list.map { it.copy(updatedAt = System.currentTimeMillis()) }
                localClassRepo.insertClasses(updated)
            }
        }.map { }
    }

    // Workers - Unified Sync Chain (Divisions -> Classes)
    private fun scheduleStructureSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val divRequest = OneTimeWorkRequestBuilder<DivisionUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()
            
        val classRequest = OneTimeWorkRequestBuilder<ClassUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        // Chain: Division -> Class
        // Using APPEND_OR_REPLACE ensures correct order even if called multiple times
        workManager.beginUniqueWork("StructureSync", ExistingWorkPolicy.APPEND_OR_REPLACE, divRequest)
            .then(classRequest)
            .enqueue()
    }
    
    // Legacy wrappers if needed, but better to update call sites to name it correctly
    private fun scheduleDivisionUploadWorker() = scheduleStructureSync()
    private fun scheduleClassUploadWorker() = scheduleStructureSync()

    // Helper for Deletions (Legacy/Manual usage if any, otherwise covered by deleteClass/deleteDivision)
    // Exposed primarily for AppDataSyncManager if it handles downstream generic deletes
    suspend fun deleteClassLocally(id: String) = localClassRepo.deleteClass(id)
    suspend fun deleteDivisionLocally(id: String) = localDivisionRepo.deleteDivision(id)
}