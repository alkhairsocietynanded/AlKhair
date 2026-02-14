package com.aewsn.alkhair.data.manager

import android.util.Log
import com.aewsn.alkhair.data.local.local_repos.LocalAppConfigRepository
import com.aewsn.alkhair.data.models.AppConfig
import com.aewsn.alkhair.data.remote.supabase.SupabaseAppConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfigRepoManager @Inject constructor(
    private val localAppConfigRepository: LocalAppConfigRepository,
    private val supabaseAppConfigRepository: SupabaseAppConfigRepository
) {

    fun observeConfigValue(key: String) = localAppConfigRepository.getConfigValue(key)
    
    suspend fun getConfigValue(key: String): String? {
        return localAppConfigRepository.getConfigByKey(key)?.value
    }

    suspend fun sync(lastSync: Long): Result<Unit> {
        return supabaseAppConfigRepository.getConfigsUpdatedAfter(lastSync)
            .onSuccess { list ->
                if (list.isNotEmpty()) {
                    localAppConfigRepository.insertConfigs(list)
                }
            }
            .map { }
    }

    suspend fun clearLocal() = localAppConfigRepository.clearAll()
}
