package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aewsn.alkhair.data.models.FeesModel
import kotlinx.coroutines.flow.Flow

@Dao
interface FeesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFee(fee: FeesModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFees(fees: List<FeesModel>)

    @Query("SELECT * FROM fees WHERE id = :id")
    fun getFeeById(id: String): Flow<FeesModel?>

    // ✅ UPDATE: Sorted by updatedAt DESC (Latest fee transaction first)
    // 'monthYear' string sorting is not reliable (Apr < Jan), so we use timestamp.
    @Query("SELECT * FROM fees WHERE user_id = :studentId ORDER BY updated_at_ms DESC")
    fun getFeesByStudentId(studentId: String): Flow<List<FeesModel>>

    // ✅ UPDATE: Sorted by updatedAt DESC (Latest entries on top)
    @Query("SELECT * FROM fees ORDER BY updated_at_ms DESC")
    fun getAllFees(): Flow<List<FeesModel>>

    @Query("DELETE FROM fees WHERE id = :id")
    suspend fun deleteFee(id: String)

    @Query("DELETE FROM fees")
    suspend fun clearAllFees()

    @Query("SELECT * FROM fees WHERE is_synced = 0")
    suspend fun getUnsyncedFees(): List<FeesModel>

    @Query("UPDATE fees SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markFeesAsSynced(ids: List<String>)
}