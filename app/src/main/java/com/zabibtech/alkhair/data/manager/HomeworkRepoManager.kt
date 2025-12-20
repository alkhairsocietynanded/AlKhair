package com.zabibtech.alkhair.data.manager

import android.net.Uri
import com.zabibtech.alkhair.data.local.local_repos.LocalHomeworkRepository
import com.zabibtech.alkhair.data.manager.base.BaseRepoManager
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.data.remote.firebase.FirebaseHomeworkRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeworkRepoManager @Inject constructor(
    private val localRepo: LocalHomeworkRepository,
    private val remoteRepo: FirebaseHomeworkRepository,
    private val storageManager: StorageManager
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
       🔁 SYNC — AppDataSyncManager ONLY
       ============================================================ */

    override suspend fun fetchRemoteUpdated(after: Long): List<Homework> =
        remoteRepo.getHomeworkUpdatedAfter(after).getOrElse { emptyList() }

    override suspend fun insertLocal(items: List<Homework>) =
        localRepo.insertHomeworkList(items)

    override suspend fun insertLocal(item: Homework) =
        localRepo.insertHomework(item)

    override suspend fun deleteLocally(id: String) =
        localRepo.deleteHomeworkById(id)

    /* ============================================================
       ✍️ WRITE — UI + File Upload Support
       ============================================================ */

    suspend fun createHomework(
        homework: Homework,
        newAttachmentUri: Uri? = null
    ): Result<Unit> {

        // 1️⃣ Upload attachment if exists
        val finalHomework = if (newAttachmentUri != null) {
            val uploadResult =
                storageManager.uploadFile(
                    fileUri = newAttachmentUri,
                    folder = "homework_attachments"
                )

            if (uploadResult.isFailure) {
                return Result.failure(uploadResult.exceptionOrNull()!!)
            }

            homework.copy(
                attachmentUrl = uploadResult.getOrThrow()
            )
        } else {
            homework
        }

        // 2️⃣ Create homework in Firebase
        return remoteRepo.createHomework(finalHomework)
            .onSuccess { insertLocal(it) }
            .map { }
    }
    suspend fun updateHomework(
        homework: Homework,
        newAttachmentUri: Uri? = null
    ): Result<Unit> {

        // 1️⃣ Handle attachment replacement if needed
        val finalHomework = if (newAttachmentUri != null) {
            val replaceResult =
                storageManager.replaceFile(
                    newFileUri = newAttachmentUri,
                    oldFileUrl = homework.attachmentUrl,
                    folder = "homework_attachments"
                )

            if (replaceResult.isFailure) {
                return Result.failure(replaceResult.exceptionOrNull()!!)
            }

            homework.copy(
                attachmentUrl = replaceResult.getOrThrow()
            )
        } else {
            homework
        }

        // 2️⃣ Update homework fields in Firebase
        return remoteRepo.updateHomework(
            finalHomework.id,
            mapOf(
                "className" to finalHomework.className,
                "division" to finalHomework.division,
                "shift" to finalHomework.shift,
                "subject" to finalHomework.subject,
                "title" to finalHomework.title,
                "description" to finalHomework.description,
                "date" to finalHomework.date,
                "teacherId" to finalHomework.teacherId,
                "attachmentUrl" to (finalHomework.attachmentUrl ?: ""),
                "updatedAt" to System.currentTimeMillis()
            )
        ).onSuccess {
            remoteRepo.getHomeworkById(finalHomework.id)
                .onSuccess { insertLocal(it) }
        }
    }



    suspend fun deleteHomework(id: String): Result<Unit> =
        remoteRepo.deleteHomework(id).onSuccess {
            deleteLocally(id)

            FirebaseRefs.deletedRecordsRef.child(id)
                .setValue(
                    DeletedRecord(
                        id = id,
                        type = "homework",
                        timestamp = System.currentTimeMillis()
                    )
                ).await()
        }
}

