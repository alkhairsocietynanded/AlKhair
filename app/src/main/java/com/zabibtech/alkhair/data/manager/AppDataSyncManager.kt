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

                coroutineScope {
                    val isFirstSync = lastSync == 0L
                    val syncTime = if (isFirstSync) 0L else lastSync

                    Log.d(
                        "AppDataSyncManager",
                        "Performing ${if (isFirstSync) "FULL" else "INCREMENTAL"} SYNC from $syncTime"
                    )

                    // Launch all sync operations in parallel
                    val userSync = async { userRepoManager.syncUsers(syncTime) }
                    val classSync = async { classDivisionRepoManager.syncClasses(syncTime) }
                    val divisionSync = async { classDivisionRepoManager.syncDivisions(syncTime) }
                    val feesSync = async { feesRepoManager.sync(syncTime) }
                    val salarySync = async { salaryRepoManager.sync(syncTime) }
                    val homeworkSync = async { homeworkRepoManager.sync(syncTime) }
                    val announcementSync =
                        async { announcementRepoManager.syncAnnouncements(syncTime) }
                    val deletionSync = async { if (!isFirstSync) syncDeletions(syncTime) }

                    // Sync Attendance for Current Month (Range Sync) - This can also run in parallel
                    val attendanceSync = async {
                        val calendar = java.util.Calendar.getInstance()
                        val todayDate = SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(calendar.time)
                        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                        val startOfMonth = SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(calendar.time)
                        attendanceRepoManager.syncAttendanceRange(startOfMonth, todayDate)
                    }

                    // Await all jobs to complete
                    listOf(
                        userSync, classSync, divisionSync, feesSync, salarySync,
                        homeworkSync, announcementSync, deletionSync, attendanceSync
                    ).forEach { it.await() }
                }

                // Update Sync Time after all operations are successful
                appDataStore.saveString(KEY_LAST_SYNC, currentTime.toString())
                Log.d("AppDataSyncManager", "Sync completed successfully")
                Result.success(Unit)

            } catch (e: Exception) {
                Log.e("AppDataSyncManager", "Sync failed", e)
                Result.failure(e)
            }
        }

    private suspend fun syncDeletions(lastSync: Long) {
        try {
            val snapshot = FirebaseRefs.deletedRecordsRef
                .orderByChild("timestamp")
                .startAt(lastSync.toDouble())
                .get()
                .await()

            val deletedRecords =
                snapshot.children.mapNotNull { it.getValue(DeletedRecord::class.java) }

            if (deletedRecords.isNotEmpty()) {
                Log.d("AppDataSyncManager", "Processing ${deletedRecords.size} deletions")
                deletedRecords.forEach { record ->
                    try {
                        when (record.type) {
                            "user" -> userRepoManager.deleteUserLocally(record.id)
                            "class" -> classDivisionRepoManager.deleteClassLocally(record.id)
                            "division" -> classDivisionRepoManager.deleteDivisionLocally(record.id)
                            "fees" -> feesRepoManager.deleteLocally(record.id)
                            "salary" -> salaryRepoManager.deleteLocally(record.id)
                            "homework" -> homeworkRepoManager.deleteLocally(record.id)
                            "announcement" -> announcementRepoManager.deleteAnnouncementLocally(
                                record.id
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "AppDataSyncManager",
                            "Failed to process deletion for ${record.id}",
                            e
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AppDataSyncManager", "Failed to sync deletions", e)
        }
    }
}
