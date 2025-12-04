package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zabibtech.alkhair.data.models.ClassModel
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(classModel: ClassModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClasses(classes: List<ClassModel>)

    @Query("SELECT * FROM classes")
    fun getAllClasses(): Flow<List<ClassModel>>

    @Query("DELETE FROM classes WHERE id = :classId")
    suspend fun deleteClass(classId: String)

    @Query("DELETE FROM classes")
    suspend fun clearAllClasses()
}