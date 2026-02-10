package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aewsn.alkhair.data.models.Syllabus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyllabusDao {

    @Query("SELECT * FROM syllabus ORDER BY updatedAt DESC")
    fun getAllSyllabus(): Flow<List<Syllabus>>

    @Query("SELECT * FROM syllabus WHERE classId = :classId ORDER BY updatedAt DESC")
    fun getSyllabusByClass(classId: String): Flow<List<Syllabus>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyllabus(syllabus: Syllabus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyllabusList(syllabusList: List<Syllabus>)

    @Query("DELETE FROM syllabus WHERE id = :id")
    suspend fun deleteSyllabusById(id: String)

    @Query("DELETE FROM syllabus")
    suspend fun clearAllSyllabus()

    @Query("SELECT * FROM syllabus WHERE isSynced = 0")
    suspend fun getUnsyncedSyllabus(): List<Syllabus>

    @Query("UPDATE syllabus SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSyllabusAsSynced(ids: List<String>)
}
