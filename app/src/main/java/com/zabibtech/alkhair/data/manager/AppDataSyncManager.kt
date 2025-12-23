package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.datastore.AppDataStore
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataSyncManager @Inject constructor(
    private val appDataStore: AppDataStore,
    private val userRepoManager: UserRepoManager,
    private val classDivisionRepoManager: ClassDivisionRepoManager,
    private val attendanceRepoManager: AttendanceRepoManager,
    private val feesRepoManager: FeesRepoManager,
    private val salaryRepoManager: SalaryRepoManager,
    private val homeworkRepoManager: HomeworkRepoManager,
    private val announcementRepoManager: AnnouncementRepoManager
) {

    companion object {
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val SYNC_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    suspend fun syncAllData(forceRefresh: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val lastSync = appDataStore.getString(KEY_LAST_SYNC).toLongOrNull() ?: 0L
                val currentTime = System.currentTimeMillis()

                if (!forceRefresh && (currentTime - lastSync) < SYNC_THRESHOLD_MS) {
                    Log.d("AppDataSyncManager", "Sync skipped: Data is fresh")
                    return@withContext Result.success(Unit)
                }

                Log.d("AppDataSyncManager", "Starting sync. Last sync: $lastSync")

                val allSuccessful = coroutineScope {
                    val isFirstSync = lastSync == 0L
                    val syncTime = if (isFirstSync) 0L else (lastSync + 1)
                    // Launch syncs in parallel
                    val jobs = listOf(
                        async { userRepoManager.sync(syncTime) },
                        async { classDivisionRepoManager.syncClasses(syncTime) },
                        async { classDivisionRepoManager.syncDivisions(syncTime) },
                        async { feesRepoManager.sync(syncTime) },
                        async { salaryRepoManager.sync(syncTime) },
                        async { homeworkRepoManager.sync(syncTime) },
                        async { announcementRepoManager.sync(syncTime) },
                        async { if (!isFirstSync) syncDeletions(syncTime) else Result.success(Unit) }
                    )

                    // Attendance Sync (Range)
                    val attendanceJob = async {
                        try {
                            val calendar = Calendar.getInstance()
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val todayDate = dateFormat.format(calendar.time)
                            calendar.set(Calendar.DAY_OF_MONTH, 1)
                            val startOfMonth = dateFormat.format(calendar.time)
                            attendanceRepoManager.syncAttendanceRange(startOfMonth, todayDate)
                            Result.success(Unit)
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }

                    // Await and check results
                    val results = (jobs + attendanceJob).map { it.await() }

                    // Return true if all succeeded (or at least critical ones)
                    results.all { it.isSuccess }
                }

                // Only update the timestamp if operations didn't crash significantly
                if (allSuccessful) {
                    appDataStore.saveString(KEY_LAST_SYNC, currentTime.toString())
                    Log.d("AppDataSyncManager", "Sync completed successfully")
                } else {
                    Log.w("AppDataSyncManager", "Sync completed with some errors")
                }

                Result.success(Unit)

            } catch (e: Exception) {
                Log.e("AppDataSyncManager", "Sync crashed", e)
                Result.failure(e)
            }
        }

    private suspend fun syncDeletions(lastSync: Long): Result<Unit> {
        return try {
            // Added +1 here as well for safety
            val queryTimestamp = (lastSync + 1).toDouble()

            val snapshot = FirebaseRefs.deletedRecordsRef
                .orderByChild("timestamp")
                .startAt(queryTimestamp)
                .get()
                .await()

            val deletedRecords =
                snapshot.children.mapNotNull { it.getValue(DeletedRecord::class.java) }

            if (deletedRecords.isNotEmpty()) {
                Log.d("AppDataSyncManager", "Processing ${deletedRecords.size} deletions")
                deletedRecords.forEach { record ->
                    try {
                        when (record.type) {
                            "user" -> userRepoManager.deleteLocally(record.id)
                            "class" -> classDivisionRepoManager.deleteClassLocally(record.id)
                            "division" -> classDivisionRepoManager.deleteDivisionLocally(record.id)
                            "fees" -> feesRepoManager.deleteLocally(record.id)
                            "salary" -> salaryRepoManager.deleteLocally(record.id)
                            "homework" -> homeworkRepoManager.deleteLocally(record.id)
                            "announcement" -> announcementRepoManager.deleteLocally(record.id)
                        }
                    } catch (e: Exception) {
                        Log.e("AppDataSyncManager", "Failed to delete: ${record.id}", e)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AppDataSyncManager", "Failed to sync deletions", e)
            Result.failure(e)
        }
    }
}