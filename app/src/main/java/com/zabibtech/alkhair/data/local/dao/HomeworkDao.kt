package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zabibtech.alkhair.data.models.Homework
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomework(homework: Homework)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeworkList(homeworkList: List<Homework>)

    @Query("SELECT * FROM homework ORDER BY date DESC")
    fun getAllHomework(): Flow<List<Homework>>

    @Query("SELECT * FROM homework WHERE className = :className AND division = :division")
    fun getHomeworkByClass(className: String, division: String): Flow<List<Homework>>

    @Query("DELETE FROM homework")
    suspend fun clearAllHomework()
}
