package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zabibtech.alkhair.data.models.FeesModel
import kotlinx.coroutines.flow.Flow

@Dao
interface FeesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFee(fee: FeesModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFees(fees: List<FeesModel>)

    @Query("SELECT * FROM fees WHERE id = :id")
    fun getFeeById(id: String): Flow<FeesModel?>

    @Query("SELECT * FROM fees WHERE studentId = :studentId")
    fun getFeesByStudentId(studentId: String): Flow<List<FeesModel>>

    @Query("SELECT * FROM fees")
    fun getAllFees(): Flow<List<FeesModel>>

    @Query("DELETE FROM fees WHERE id = :id")
    suspend fun deleteFee(id: String)

    @Query("DELETE FROM fees")
    suspend fun clearAllFees()
}
