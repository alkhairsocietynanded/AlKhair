package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aewsn.alkhair.data.models.AppConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config")
    fun getAllConfigs(): Flow<List<AppConfig>>

    @Query("SELECT * FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getConfigByKey(key: String): AppConfig?
    
    @Query("SELECT value FROM app_config WHERE `key` = :key LIMIT 1")
    fun getConfigValueFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AppConfig)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<AppConfig>)

    @Query("DELETE FROM app_config WHERE `key` = :key")
    suspend fun deleteConfig(key: String)

    @Query("DELETE FROM app_config")
    suspend fun clearAll()
}
