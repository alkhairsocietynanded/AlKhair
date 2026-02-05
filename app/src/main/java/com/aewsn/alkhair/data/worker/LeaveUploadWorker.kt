package com.aewsn.alkhair.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.local.local_repos.LocalLeaveRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseLeaveRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class LeaveUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val localLeaveRepository: LocalLeaveRepository,
    private val supabaseLeaveRepository: SupabaseLeaveRepository,
    private val pendingDeletionDao: PendingDeletionDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            var isSyncSuccessful = true

            // =========================================================================
            // TASK 1: Upload Unsynced Leaves (Create / Update)
            // =========================================================================
            val unsyncedLeaves = localLeaveRepository.getUnsyncedLeaves().first()
            if (unsyncedLeaves.isNotEmpty()) {
                Log.d("LeaveUploadWorker", "Found ${unsyncedLeaves.size} unsynced leaves. Uploading...")
                
                for (leave in unsyncedLeaves) {
                    // SAFETY CHECK: Validate UUIDs to prevent "invalid input syntax" errors
                    if (leave.studentId.isEmpty() || leave.id.isEmpty()) {
                        Log.e("LeaveUploadWorker", "Found corrupt leave (empty ID/StudentID). Deleting... Data: $leave")
                        localLeaveRepository.deleteLeave(leave.id) // Remove bad data locally so sync unblocks
                        continue
                    }

                    // Try to upload to server (SupabaseRepo.applyLeave uses Upsert)
                    val result = supabaseLeaveRepository.applyLeave(leave)
                    
                    if (result.isSuccess) {
                        val remoteLeave = result.getOrNull()
                        if (remoteLeave != null) {
                            // Update local as synced
                            val syncedLeave = remoteLeave.copy(isSynced = true)
                            localLeaveRepository.insertLeave(syncedLeave)
                            Log.d("LeaveUploadWorker", "Uploaded leave: ${leave.id}")
                        }
                    } else {
                        Log.e("LeaveUploadWorker", "Failed to upload leave: ${leave.id}", result.exceptionOrNull())
                        isSyncSuccessful = false
                    }
                }
            }

            // =========================================================================
            // TASK 2: Handle Pending Deletions
            // =========================================================================
            val pendingDeletions = pendingDeletionDao.getPendingDeletionsByType("LEAVE")
            if (pendingDeletions.isNotEmpty()) {
                Log.d("LeaveUploadWorker", "Found ${pendingDeletions.size} pending deletions")
                
                for (deletion in pendingDeletions) {
                   val result = supabaseLeaveRepository.deleteLeave(deletion.id)
                   if (result.isSuccess) {
                       pendingDeletionDao.removePendingDeletion(deletion.id) 
                       Log.d("LeaveUploadWorker", "Deleted remote leave: ${deletion.id}")
                   } else {
                       Log.e("LeaveUploadWorker", "Failed to delete remote leave: ${deletion.id}", result.exceptionOrNull())
                       isSyncSuccessful = false
                   }
                }
            }

            // =========================================================================
            // RETURN RESULT
            // =========================================================================
            if (isSyncSuccessful) {
                Result.success()
            } else {
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e("LeaveUploadWorker", "Fatal error in LeaveUploadWorker", e)
            Result.retry()
        }
    }
}
