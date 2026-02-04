package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aewsn.alkhair.data.models.PendingDeletion

@Dao
interface PendingDeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDeletion(deletion: PendingDeletion)

    @Query("SELECT * FROM pending_deletions WHERE type = :type")
    suspend fun getPendingDeletionsByType(type: String): List<PendingDeletion>

    @Query("DELETE FROM pending_deletions WHERE id = :id")
    suspend fun removePendingDeletion(id: String)
        
    @Query("DELETE FROM pending_deletions WHERE id IN (:ids)")
    suspend fun removePendingDeletions(ids: List<String>)
}
