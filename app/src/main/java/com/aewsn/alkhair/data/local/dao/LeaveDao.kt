package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aewsn.alkhair.data.models.Leave
import kotlinx.coroutines.flow.Flow

@Dao
interface LeaveDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeave(leave: Leave)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaves(leaves: List<Leave>)

    @Update
    suspend fun updateLeave(leave: Leave)

    @Query("SELECT * FROM leaves WHERE student_id = :studentId ORDER BY start_date DESC")
    fun getLeavesByStudent(studentId: String): Flow<List<Leave>>

    @Query("SELECT * FROM leaves WHERE id = :id")
    suspend fun getLeaveById(id: String): Leave?

    @Query("DELETE FROM leaves WHERE id = :id")
    suspend fun deleteLeaveById(id: String)

    @Query("SELECT * FROM leaves WHERE is_synced = 0")
    fun getUnsyncedLeaves(): Flow<List<Leave>>

    @Query("DELETE FROM leaves")
    suspend fun clearLeaves()

    // Admin: Get all leaves with student names and roles
    @Query("""
        SELECT leaves.*, users.name as student_name, users.role as student_role 
        FROM leaves 
        INNER JOIN users ON leaves.student_id = users.uid 
        ORDER BY leaves.start_date DESC
    """)
    fun getAllLeaves(): Flow<List<com.aewsn.alkhair.data.models.LeaveWithStudent>>

    // Teacher: Get leaves for specific class (Join with Users)
    @Query("""
        SELECT leaves.*, users.name as student_name, users.role as student_role 
        FROM leaves 
        INNER JOIN users ON leaves.student_id = users.uid 
        WHERE users.classId = :classId AND users.role = 'student'
        ORDER BY leaves.start_date DESC
    """)
    fun getLeavesByClass(classId: String): Flow<List<com.aewsn.alkhair.data.models.LeaveWithStudent>>
}
