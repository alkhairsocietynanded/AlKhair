package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.aewsn.alkhair.data.models.Timetable
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable WHERE class_id = :classId ORDER BY day_of_week, period_index ASC")
    fun getTimetableForClass(classId: String): Flow<List<Timetable>>

    @Query("SELECT * FROM timetable WHERE user_id = :teacherId ORDER BY day_of_week, period_index ASC")
    fun getTimetableForTeacher(teacherId: String): Flow<List<Timetable>>
    
    @Query("SELECT * FROM timetable ORDER BY day_of_week, period_index ASC")
    fun getAllTimetables(): Flow<List<Timetable>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetable(timetable: Timetable)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetables(timetables: List<Timetable>)

    @Query("DELETE FROM timetable WHERE id = :id")
    suspend fun deleteTimetable(id: String)
    
    @Query("DELETE FROM timetable")
    suspend fun clearall()
    
    // For joining names, we might need a POJO or do it in Repository/ViewModel. 
    // Basic query is enough for now as we can fetch subjects/teachers separately or map them.
    
    @Query("SELECT * FROM timetable WHERE is_synced = 0")
    suspend fun getUnsyncedTimetables(): List<Timetable>

    @Query("UPDATE timetable SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markTimetablesAsSynced(ids: List<String>)
}
