package com.aewsn.alkhair.data.manager

import android.net.Uri
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalSyllabusRepository
import com.aewsn.alkhair.data.manager.base.BaseRepoManager
import com.aewsn.alkhair.data.models.PendingDeletion
import com.aewsn.alkhair.data.models.Syllabus
import com.aewsn.alkhair.data.remote.supabase.SupabaseSyllabusRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.aewsn.alkhair.data.worker.SyllabusUploadWorker
import java.util.concurrent.TimeUnit

@Singleton
class SyllabusRepoManager @Inject constructor(
    private val localRepo: LocalSyllabusRepository,
    private val remoteRepo: SupabaseSyllabusRepository,
    private val storageManager: StorageManager,
    private val pendingDeletionDao: PendingDeletionDao,
    private val workManager: WorkManager
) : BaseRepoManager<Syllabus>() {

    /* ============================================================
       📦 SSOT — ROOM
       ============================================================ */

    override fun observeLocal(): Flow<List<Syllabus>> =
        localRepo.getAllSyllabus()
    
    fun observeClassSyllabus(classId: String): Flow<List<Syllabus>> =
        localRepo.getSyllabusByClass(classId)

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */


    // 1. Global Sync (Admin)
    override suspend fun fetchRemoteUpdated(after: Long): List<Syllabus> {
        return remoteRepo.getSyllabusUpdatedAfter(after).getOrElse { emptyList() }
    }

    // 2. Class Targeted Sync (Student)
    suspend fun syncClassSyllabus(classId: String, after: Long): Result<Unit> {
        android.util.Log.d("SyllabusRepoManager", "Syncing syllabus for class: $classId after: $after")
        return remoteRepo.getSyllabusForClassUpdatedAfter(classId, after)
            .onSuccess { list ->
                android.util.Log.d("SyllabusRepoManager", "Fetched ${list.size} syllabus items.")
                if (list.isNotEmpty()) {
                    insertLocal(list)
                }
            }
            .onFailure { e ->
                android.util.Log.e("SyllabusRepoManager", "Failed to sync syllabus", e)
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    suspend fun createSyllabus(
        syllabus: Syllabus,
        attachmentUri: Uri? = null
    ): Result<Unit> {
        
        var finalSyllabus = syllabus

        // 1. Upload Attachment (Synchronous for now, ideally background)
        if (attachmentUri != null) {
            val uploadResult = storageManager.uploadFile(
                fileUri = attachmentUri,
                folder = "syllabus_attachments"
            )
            if (uploadResult.isFailure) return Result.failure(uploadResult.exceptionOrNull()!!)
            
            finalSyllabus = finalSyllabus.copy(attachmentUrl = uploadResult.getOrThrow())
        }

        // 2. Prepare Local Record
        val syllabusWithSyncStatus = finalSyllabus.copy(
            id = finalSyllabus.id.ifEmpty { java.util.UUID.randomUUID().toString() },
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )

        // 3. Save Local
        insertLocal(syllabusWithSyncStatus)

        // 4. Schedule Worker
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    suspend fun updateSyllabus(
        syllabus: Syllabus,
        attachmentUri: Uri? = null
    ): Result<Unit> {
        // Just reuse create logic as it handles upsert (if ID exists) and attachment upload
        // ensuring we update timestamp and sync status
        return createSyllabus(syllabus, attachmentUri)
    }

    suspend fun deleteSyllabus(id: String): Result<Unit> {
        deleteLocally(id)
        
        val pendingDeletion = PendingDeletion(
            id = id,
            type = "SYLLABUS",
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

        val uploadWorkRequest = OneTimeWorkRequestBuilder<SyllabusUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "SyllabusUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    // --- Base Implementations ---
    override suspend fun insertLocal(items: List<Syllabus>) = localRepo.insertSyllabusList(items)
    override suspend fun insertLocal(item: Syllabus) = localRepo.insertSyllabus(item)
    override suspend fun deleteLocally(id: String) = localRepo.deleteSyllabusById(id)
    override suspend fun clearLocal() = localRepo.clearAll()
}
