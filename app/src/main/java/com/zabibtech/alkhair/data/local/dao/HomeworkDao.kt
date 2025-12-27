package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zabibtech.alkhair.data.models.Homework
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeworkDao {

    // ✅ WRITE OPERATIONS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomework(homework: Homework)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeworkList(homeworkList: List<Homework>)

    @Query("DELETE FROM homework WHERE id = :homeworkId")
    suspend fun deleteHomeworkById(homeworkId: String)

    @Query("DELETE FROM homework")
    suspend fun clearAllHomework()

    // ✅ READ OPERATIONS

    // 1. Admin View (No Filter)
    @Query("SELECT * FROM homework ORDER BY date DESC")
    fun getAllHomework(): Flow<List<Homework>>

    // 2. Student View (Specific Class + Division)
    // Example: Class 10, Section A
    @Query("SELECT * FROM homework WHERE className = :className AND division = :division ORDER BY date DESC")
    fun getHomeworkByClassAndDivision(className: String, division: String): Flow<List<Homework>>

    // 3. Teacher View (Broad Class Filter)
    // Example: Class 10 (All Sections)
    @Query("SELECT * FROM homework WHERE className = :className ORDER BY date DESC")
    fun getHomeworkByClassName(className: String): Flow<List<Homework>>
}