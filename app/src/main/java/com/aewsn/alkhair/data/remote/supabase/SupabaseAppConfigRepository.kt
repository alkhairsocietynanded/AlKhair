package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAppConfigRepository @Inject constructor(
    private val supabase: SupabaseClient
) {
    suspend fun getConfigsUpdatedAfter(timestamp: Long): Result<List<AppConfig>> {
        return try {
            val list = supabase.from("app_config").select {
                filter {
                    gt("updated_at_ms", timestamp)
                }
            }.decodeList<AppConfig>()
            Result.success(list)
        } catch (e: Exception) {
            Log.e("SupabaseAppConfigRepo", "Error fetching updated configs", e)
            Result.failure(e)
        }
    }
}
