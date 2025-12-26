package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zabibtech.alkhair.data.models.Attendance
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceList(attendanceList: List<Attendance>)

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date = :date")
    fun getAttendance(studentId: String, date: String): Flow<Attendance?>

    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceByDate(date: String): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId")
    fun getAttendanceByStudent(studentId: String): Flow<List<Attendance>>

    // ✅ ADDED for BaseRepoManager
    @Query("SELECT * FROM attendance")
    fun getAllAttendance(): Flow<List<Attendance>>



    @Query("DELETE FROM attendance")
    suspend fun clearAllAttendance()
}