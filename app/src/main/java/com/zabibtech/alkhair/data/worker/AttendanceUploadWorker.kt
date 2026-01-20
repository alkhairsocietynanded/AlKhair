package com.zabibtech.alkhair.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zabibtech.alkhair.data.local.local_repos.LocalAttendanceRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAttendanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class AttendanceUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val localAttendanceRepository: LocalAttendanceRepository,
    private val firebaseAttendanceRepository: FirebaseAttendanceRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Task 1: Upload Unsynced Attendance
            val unsyncedAttendance = localAttendanceRepository.getUnsyncedAttendance()
            if (unsyncedAttendance.isNotEmpty()) {
                val result = firebaseAttendanceRepository.saveAttendanceBatch(unsyncedAttendance)
                if (result.isSuccess) {
                    unsyncedAttendance.forEach { 
                        localAttendanceRepository.markAttendanceAsSynced(it.studentId, it.classId, it.date)
                    }
                } else {
                    return@withContext Result.retry()
                }
            }

            // Note: Deletion for attendance usually not supported via generic 'PendingDeletion' 
            // due to composite keys. If needed, a specific 'PendingAttendanceDeletion' table 
            // or logic would be required. Skipping for now as deletions weren't in original scope or repo manager.

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
