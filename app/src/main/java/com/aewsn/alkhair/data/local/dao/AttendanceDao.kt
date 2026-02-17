package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aewsn.alkhair.data.models.Attendance
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceList(attendanceList: List<Attendance>)

    @Query("SELECT * FROM attendance WHERE user_id = :studentId AND date = :date")
    fun getAttendance(studentId: String, date: String): Flow<Attendance?>

    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceByDate(date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE user_id = :studentId")
    fun getAttendanceByStudent(studentId: String): Flow<List<Attendance>>

    // ✅ ADDED for BaseRepoManager
    @Query("SELECT * FROM attendance")
    fun getAllAttendance(): Flow<List<Attendance>>

    @Query("DELETE FROM attendance")
    suspend fun clearAllAttendance()

    @Query("SELECT * FROM attendance WHERE is_synced = 0")
    suspend fun getUnsyncedAttendance(): List<Attendance>

    @Query("UPDATE attendance SET is_synced = 1 WHERE user_id = :studentId AND class_id = :classId AND date = :date")
    suspend fun markAttendanceAsSynced(studentId: String, classId: String, date: String)
}