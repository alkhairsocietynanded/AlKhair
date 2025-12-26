package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.datastore.AppDataStore
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
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
        private const val TAG = "AppDataSyncManager"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val SYNC_THRESHOLD_MS = 1 * 60 * 1000L // 5 min
    }

    // 🔒 Prevent parallel syncs
    private val syncMutex = Mutex()
    private var isSyncRunning = false

    /**
     * 🔁 Public entry point
     */
    suspend fun syncAllData(forceRefresh: Boolean = false): Result<Unit> {
        return syncMutex.withLock {
            if (isSyncRunning) {
                Log.d(TAG, "Sync already running → skipping")
                return Result.success(Unit)
            }

            isSyncRunning = true
            try {
                performSync(forceRefresh)
            } finally {
                isSyncRunning = false
            }
        }
    }

    /**
     * 🧠 Actual sync logic
     */
    private suspend fun performSync(forceRefresh: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val lastSync =
                    appDataStore.getString(KEY_LAST_SYNC).toLongOrNull() ?: 0L
                val now = System.currentTimeMillis()

                if (!forceRefresh && lastSync != 0L && (now - lastSync) < SYNC_THRESHOLD_MS) {
                    Log.d(TAG, "Sync skipped → data is fresh")
                    return@withContext Result.success(Unit)
                }

                val isFirstSync = lastSync == 0L
                val queryAfter = if (isFirstSync) 0L else lastSync + 1

                Log.d(
                    TAG,
                    "Starting sync. Last Sync Found: $lastSync, Querying After: $queryAfter"
                )

                coroutineScope {

                    val jobs = listOf(

                        async {
                            runCatching {
                                userRepoManager.sync(queryAfter)
                            }
                        },

                        async {
                            runCatching {
                                classDivisionRepoManager.syncClasses(queryAfter)
                            }
                        },

                        async {
                            runCatching {
                                classDivisionRepoManager.syncDivisions(queryAfter)
                            }
                        },

                        async {
                            runCatching {
                                feesRepoManager.sync(queryAfter)
                            }
                        },

                        async {
                            runCatching {
                                salaryRepoManager.sync(queryAfter)
                            }
                        },

                        async {
                            runCatching {
                                homeworkRepoManager.sync(queryAfter)
                            }
                        },

                        async {
                            runCatching {
                                announcementRepoManager.sync(queryAfter)
                            }
                        },

                        async {
                            if (!isFirstSync) {
                                runCatching {
                                    syncDeletions(queryAfter)
                                }
                            }
                        }
                    )

                    // 📅 Attendance monthly range sync (always)
                    val attendanceJob = async {
                        runCatching {
                            val cal = Calendar.getInstance()
                            val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val end = df.format(cal.time)
                            cal.set(Calendar.DAY_OF_MONTH, 1)
                            val start = df.format(cal.time)
                            attendanceRepoManager.syncAttendanceRange(start, end)
                        }
                    }

                    (jobs + attendanceJob).awaitAll()
                }

                // ✅ Update timestamp ONLY after successful sync
                appDataStore.saveString(KEY_LAST_SYNC, now.toString())
                Log.d(TAG, "Sync completed successfully. New Timestamp: $now")

                Result.success(Unit)

            } catch (e: CancellationException) {
                Log.w(TAG, "Sync cancelled")
                throw e // IMPORTANT
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                Result.failure(e)
            }
        }

    /**
     * 🗑️ Tombstone delete sync
     */
    private suspend fun syncDeletions(after: Long) {
        val snapshot = FirebaseRefs.deletedRecordsRef
            .orderByChild("timestamp")
            .startAt(after.toDouble())
            .get()
            .await()

        val records = snapshot.children.mapNotNull {
            it.getValue(DeletedRecord::class.java)
        }

        if (records.isEmpty()) return

        Log.d(TAG, "Processing ${records.size} deletions")

        records.forEach { record ->
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
                Log.e(TAG, "Failed deleting ${record.type} → ${record.id}", e)
            }
        }
    }
}
