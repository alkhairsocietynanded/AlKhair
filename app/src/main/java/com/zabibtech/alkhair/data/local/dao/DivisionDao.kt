package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zabibtech.alkhair.data.models.DivisionModel
import kotlinx.coroutines.flow.Flow

@Dao
interface DivisionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDivision(division: DivisionModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDivisions(divisions: List<DivisionModel>)

    @Query("SELECT * FROM divisions")
    fun getAllDivisions(): Flow<List<DivisionModel>>

    @Query("DELETE FROM divisions WHERE id = :divisionId")
    suspend fun deleteDivision(divisionId: String)

    @Query("DELETE FROM divisions")
    suspend fun clearAllDivisions()

    @Query("SELECT * FROM divisions WHERE isSynced = 0")
    suspend fun getUnsyncedDivisions(): List<DivisionModel>

    @Query("UPDATE divisions SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markDivisionsAsSynced(ids: List<String>)
}