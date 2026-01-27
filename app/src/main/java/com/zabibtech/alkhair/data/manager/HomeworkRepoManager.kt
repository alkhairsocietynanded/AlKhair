package com.zabibtech.alkhair.data.manager

import android.net.Uri
import com.zabibtech.alkhair.data.local.local_repos.LocalHomeworkRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.data.remote.supabase.SupabaseHomeworkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.zabibtech.alkhair.data.local.dao.PendingDeletionDao
import com.zabibtech.alkhair.data.models.PendingDeletion
import com.zabibtech.alkhair.data.worker.HomeworkUploadWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

@Singleton
class HomeworkRepoManager @Inject constructor(
    private val localRepo: LocalHomeworkRepository,
    private val remoteRepo: SupabaseHomeworkRepository,
    private val storageManager: StorageManager,
    private val pendingDeletionDao: PendingDeletionDao,

    private val workManager: WorkManager,
    private val classRepo: com.zabibtech.alkhair.data.local.local_repos.LocalClassRepository,
    private val divisionRepo: com.zabibtech.alkhair.data.local.local_repos.LocalDivisionRepository
) : BaseRepoManager<Homework>() {

    /* ============================================================
       📦 SSOT — ROOM
       ============================================================ */

    override fun observeLocal(): Flow<List<Homework>> =
        localRepo.getAllHomework()

    fun observeFiltered(
        className: String?,
        division: String?
    ): Flow<List<Homework>> =
        localRepo.observeHomeworkFiltered(className, division)

    /* ============================================================
       🔁 SYNC LOGIC
       ============================================================ */

    // 1. Global Sync (Admin)
    override suspend fun fetchRemoteUpdated(after: Long): List<Homework> {
        val remoteList = remoteRepo.getHomeworkUpdatedAfter(after).getOrElse { emptyList() }
        // Hydrate className AND division locally
        return remoteList.map { homework ->
            val fetchedClass = classRepo.getClassById(homework.classId)
            var divName = ""
            if (fetchedClass?.divisionId != null) {
                val fetchedDiv = divisionRepo.getDivisionById(fetchedClass.divisionId)
                divName = fetchedDiv?.name ?: ""
            }
            homework.copy(
                className = fetchedClass?.className ?: "",
                divisionName = divName
            )
        }
    }

    // 2. Class Targeted Sync (Student) - ✅ New Optimization
    suspend fun syncClassHomework(classId: String, shift: String, lastSync: Long): Result<Unit> {
        return remoteRepo.getHomeworkForClassAndShiftUpdatedAfter(classId, shift, lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    // Hydrate with known name if possible, or lookup
                    val fetchedClass = classRepo.getClassById(classId)
                    val className = fetchedClass?.className ?: ""
                    var divName = ""
                    if (fetchedClass?.divisionId != null) {
                         val fetchedDiv = divisionRepo.getDivisionById(fetchedClass.divisionId)
                         divName = fetchedDiv?.name ?: ""
                    }
                    
                    val hydratedList = list.map { it.copy(className = className, divisionName = divName) }
                    insertLocal(hydratedList)
                }
            }
            .map { }
    }

    /* ============================================================
       ✍️ WRITE — (Local First -> Background Sync)
       ============================================================ */

    suspend fun createHomework(
        homework: Homework,
        newAttachmentUri: Uri? = null
    ): Result<Unit> {

        // 1️⃣ Upload attachment if exists (Ideally this should also be generic/background, but keeping it here for now)
        // Note: For true offline-first with attachments, we'd need to cache the URI and upload in Worker.
        // For now, keeping the attachment upload synchronous as per current scope, but saving DB record locally first.
        
        var finalHomework = homework
        
        if (newAttachmentUri != null) {
            val uploadResult = storageManager.uploadFile(
                fileUri = newAttachmentUri,
                folder = "homework_attachments"
            )

            if (uploadResult.isFailure) {
                return Result.failure(uploadResult.exceptionOrNull()!!)
            }
            finalHomework = homework.copy(
                attachmentUrl = uploadResult.getOrThrow()
            )
        }
        
        // 2️⃣ Prepare Local Record
        val homeworkWithSyncStatus = finalHomework.copy(
            id = finalHomework.id.ifEmpty { java.util.UUID.randomUUID().toString() }, // Ensure ID
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )

        // 3️⃣ Save to Local immediately
        insertLocal(homeworkWithSyncStatus)
        
        // 4️⃣ Schedule Background Upload
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    suspend fun updateHomework(
        homework: Homework,
        newAttachmentUri: Uri? = null
    ): Result<Unit> {

        // 1️⃣ Handle attachment replacement
        var finalHomework = homework
        
        if (newAttachmentUri != null) {
            val replaceResult = storageManager.replaceFile(
                newFileUri = newAttachmentUri,
                oldFileUrl = homework.attachmentUrl,
                folder = "homework_attachments"
            )

            if (replaceResult.isFailure) {
                return Result.failure(replaceResult.exceptionOrNull()!!)
            }
            finalHomework = homework.copy(
                attachmentUrl = replaceResult.getOrThrow()
            )
        }

        // 2️⃣ Prepare Local Record
        val updatedLocal = finalHomework.copy(
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        
        // 3️⃣ Save Local
        insertLocal(updatedLocal)
        
        // 4️⃣ Schedule Background Sync
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }

    suspend fun deleteHomework(id: String): Result<Unit> {
        // 1. Delete Local
        deleteLocally(id)
        
        // 2. Mark for deletion
        val pendingDeletion = PendingDeletion(
            id = id,
            type = "HOMEWORK",
            timestamp = System.currentTimeMillis()
        )
        pendingDeletionDao.insertPendingDeletion(pendingDeletion)
        
        // 3. Schedule Worker
        scheduleUploadWorker()
        
        return Result.success(Unit)
    }
    
    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<HomeworkUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            "HomeworkUploadWork",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadWorkRequest
        )
    }

    // --- Base Implementations ---
    override suspend fun insertLocal(items: List<Homework>) = localRepo.insertHomeworkList(items)
    override suspend fun insertLocal(item: Homework) = localRepo.insertHomework(item)
    override suspend fun deleteLocally(id: String) = localRepo.deleteHomeworkById(id)
    override suspend fun clearLocal() = localRepo.clearAll()
}