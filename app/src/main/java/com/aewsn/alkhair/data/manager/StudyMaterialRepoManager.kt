package com.aewsn.alkhair.data.manager

import android.net.Uri
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalStudyMaterialRepository
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.models.StudyMaterial
import com.aewsn.alkhair.data.remote.supabase.SupabaseStudyMaterialRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.aewsn.alkhair.data.worker.StudyMaterialUploadWorker
import java.util.concurrent.TimeUnit

@Singleton
class StudyMaterialRepoManager @Inject constructor(
    private val localRepo: LocalStudyMaterialRepository,
    private val remoteRepo: SupabaseStudyMaterialRepository,
    private val storageManager: StorageManager,
    private val pendingDeletionDao: PendingDeletionDao,
    private val workManager: WorkManager
) : BaseRepoManager<StudyMaterial>() {

    /* ============================================================
       📦 SSOT — ROOM
       ============================================================ */

    override fun observeLocal(): Flow<List<StudyMaterial>> =
        localRepo.getAllStudyMaterials()

    fun observeClassMaterials(classId: String): Flow<List<StudyMaterial>> =
        localRepo.getStudyMaterialsByClass(classId)

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */

    // 1. Global Sync (Admin)
    override suspend fun fetchRemoteUpdated(after: Long): List<StudyMaterial> {
        return remoteRepo.getUpdatedAfter(after).getOrElse { emptyList() }
    }

    // 2. Class Targeted Sync (Student/Teacher)
    suspend fun syncClassMaterials(classId: String, after: Long): Result<Unit> {
        android.util.Log.d("StudyMaterialRepoManager", "Syncing materials for class: $classId after: $after")
        return remoteRepo.getForClassUpdatedAfter(classId, after)
            .onSuccess { list ->
                android.util.Log.d("StudyMaterialRepoManager", "Fetched ${list.size} study material items.")
                if (list.isNotEmpty()) {
                    insertLocal(list)
                }
            }
            .onFailure { e ->
                android.util.Log.e("StudyMaterialRepoManager", "Failed to sync study materials", e)
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    suspend fun createMaterial(
        material: StudyMaterial,
        attachmentUri: Uri? = null
    ): Result<Unit> {

        var finalMaterial = material

        // 1. Upload Attachment
        if (attachmentUri != null) {
            val uploadResult = storageManager.uploadFile(
                fileUri = attachmentUri,
                folder = "study_material_attachments"
            )
            if (uploadResult.isFailure) return Result.failure(uploadResult.exceptionOrNull()!!)

            finalMaterial = finalMaterial.copy(attachmentUrl = uploadResult.getOrThrow())
        }

        // 2. Prepare Local Record
        val materialWithSyncStatus = finalMaterial.copy(
            id = finalMaterial.id.ifEmpty { java.util.UUID.randomUUID().toString() },
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )

        // 3. Save Local
        insertLocal(materialWithSyncStatus)

        // 4. Schedule Worker
        scheduleUploadWorker()

        return Result.success(Unit)
    }

    suspend fun updateMaterial(
        material: StudyMaterial,
        attachmentUri: Uri? = null
    ): Result<Unit> {
        return createMaterial(material, attachmentUri)
    }

    suspend fun deleteMaterial(id: String): Result<Unit> {
        deleteLocally(id)

        val pendingDeletion = PendingDeletion(
            id = id,
            type = "STUDY_MATERIAL",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)

        // Schedule Worker
        scheduleUploadWorker()

        return Result.success(Unit)
    }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<StudyMaterialUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "StudyMaterialUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    // --- Base Implementations ---
    override suspend fun insertLocal(items: List<StudyMaterial>) = localRepo.insertStudyMaterialList(items)
    override suspend fun insertLocal(item: StudyMaterial) = localRepo.insertStudyMaterial(item)
    override suspend fun deleteLocally(id: String) = localRepo.deleteStudyMaterialById(id)
    override suspend fun clearLocal() = localRepo.clearAll()
}
