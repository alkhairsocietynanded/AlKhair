package com.aewsn.alkhair.data.local.local_repos

import com.aewsn.alkhair.data.local.dao.AppConfigDao
import com.aewsn.alkhair.data.models.AppConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAppConfigRepository @Inject constructor(
    private val appConfigDao: AppConfigDao
) {
    fun getAllConfigs(): Flow<List<AppConfig>> = appConfigDao.getAllConfigs()

    fun getConfigValue(key: String): Flow<String?> = appConfigDao.getConfigValueFlow(key)

    suspend fun getConfigByKey(key: String): AppConfig? = appConfigDao.getConfigByKey(key)

    suspend fun insertConfig(config: AppConfig) = appConfigDao.insertConfig(config)

    suspend fun insertConfigs(configs: List<AppConfig>) = appConfigDao.insertConfigs(configs)

    suspend fun deleteConfig(key: String) = appConfigDao.deleteConfig(key)

    suspend fun clearAll() = appConfigDao.clearAll()
}
