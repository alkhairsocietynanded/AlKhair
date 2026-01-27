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

    @Query("SELECT * FROM classes")
    suspend fun getAllClassesOneShot(): List<ClassModel>

    @Query("SELECT * FROM classes WHERE id = :classId LIMIT 1")
    suspend fun getClassById(classId: String): ClassModel?

    @Query("DELETE FROM classes WHERE id = :classId")
    suspend fun deleteClass(classId: String)

    @Query("DELETE FROM classes")
    suspend fun clearAllClasses()

    @Query("SELECT * FROM classes WHERE isSynced = 0")
    suspend fun getUnsyncedClasses(): List<ClassModel>

    @Query("UPDATE classes SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markClassesAsSynced(ids: List<String>)
}