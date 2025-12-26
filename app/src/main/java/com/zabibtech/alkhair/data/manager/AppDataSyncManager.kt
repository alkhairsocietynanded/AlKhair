package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.datastore.AppDataStore
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

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
        private const val SYNC_THRESHOLD_MS = 1 * 60 * 1000L // 1 min
    }

    private val syncMutex = Mutex()
    private var isSyncRunning = false

    /**
     * 🔁 Public entry point
     */
    suspend fun syncAllData(forceRefresh: Boolean = false): Result<Unit> {
        // Fast check without locking
        if (isSyncRunning) {
            Log.d(TAG, "⚠️ Sync skipped: Already running.")
            return Result.success(Unit)
        }

        return syncMutex.withLock {
            if (isSyncRunning) return Result.success(Unit)
            isSyncRunning = true
            try {
                performSync(forceRefresh)
            } finally {
                isSyncRunning = false
            }
        }
    }

    private suspend fun performSync(forceRefresh: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val lastSync = appDataStore.getString(KEY_LAST_SYNC).toLongOrNull() ?: 0L
                val deviceTime = System.currentTimeMillis()

                if (!forceRefresh && lastSync != 0L && (deviceTime - lastSync) < SYNC_THRESHOLD_MS) {
                    Log.d(TAG, "Sync skipped → Data is fresh")
                    return@withContext Result.success(Unit)
                }

                val isFirstSync = lastSync == 0L
                val queryTime = if (isFirstSync) 0L else lastSync + 1

                Log.d(TAG, "Starting sync. Last Sync: $lastSync, Querying After: $queryTime")

                supervisorScope {

                    // ✅ GROUP 1: Timestamp Jobs
                    val timestampJobs = listOf(
                        async { runCatching { userRepoManager.sync(queryTime) } },
                        async { runCatching { feesRepoManager.sync(queryTime) } },
                        async { runCatching { salaryRepoManager.sync(queryTime) } },
                        async { runCatching { homeworkRepoManager.sync(queryTime) } },
                        async { runCatching { announcementRepoManager.sync(queryTime) } },
                        async { runCatching { attendanceRepoManager.sync(queryTime) } }
                    )

                    // ✅ GROUP 2: Void Jobs
                    val voidJobs = listOf(
                        async { runCatching { classDivisionRepoManager.syncClasses(queryTime) } },
                        async { runCatching { classDivisionRepoManager.syncDivisions(queryTime) } },
                        async {
                            if (!isFirstSync) {
                                runCatching { syncDeletions(queryTime) }
                            } else {
                                Result.success(Unit)
                            }
                        }
                    )

                    val timestampResults = timestampJobs.awaitAll()
                    voidJobs.awaitAll()

                    // ✅ FIX: Type-Safe Max Timestamp Calculation
                    var maxDataTimestamp = lastSync

                    timestampResults.forEach { outerResult ->
                        outerResult.onSuccess { innerResult ->
                            innerResult.onSuccess { data ->
                                // Check if data is Long (Timestamp) before comparing
                                if (data is Long && data > maxDataTimestamp) {
                                    maxDataTimestamp = data
                                }
                            }
                        }
                    }

                    // Save Timestamp
                    val newSyncTime = max(deviceTime, maxDataTimestamp)
                    appDataStore.saveString(KEY_LAST_SYNC, newSyncTime.toString())
                    Log.d(TAG, "✅ Sync completed. New Timestamp: $newSyncTime")
                }

                Result.success(Unit)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Sync crashed", e)
                Result.failure(e)
            }
        }

    /**
     * 🗑️ Tombstone delete sync
     */
    private suspend fun syncDeletions(after: Long): Result<Unit> {
        return try {
            val snapshot = FirebaseRefs.deletedRecordsRef
                .orderByChild("timestamp")
                .startAt(after.toDouble())
                .get()
                .await()

            val deletedRecords =
                snapshot.children.mapNotNull { it.getValue(DeletedRecord::class.java) }

            if (deletedRecords.isNotEmpty()) {
                Log.d(TAG, "Processing ${deletedRecords.size} deletions")
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
                        Log.e(TAG, "Failed deleting ${record.type} → ${record.id}", e)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Deletion sync failed", e)
            Result.failure(e)
        }
    }
}