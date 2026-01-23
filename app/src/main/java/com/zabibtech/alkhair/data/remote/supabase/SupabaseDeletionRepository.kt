package com.zabibtech.alkhair.data.remote.supabase

import android.util.Log
import com.zabibtech.alkhair.data.models.DeletedRecord
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseDeletionRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    companion object {
        private const val TABLE_NAME = "deleted_records"
        private const val TAG = "SupabaseDeletionRepo"
    }

    suspend fun getDeletedRecords(afterTimestamp: Long): Result<List<DeletedRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching deletions after: $afterTimestamp")
                val records = supabaseClient.from(TABLE_NAME)
                    .select {
                        filter {
                            gt("timestamp", afterTimestamp)
                        }
                    }.decodeList<DeletedRecord>()
                
                Result.success(records)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching deleted records", e)
                Result.failure(e)
            }
        }
    }
}
