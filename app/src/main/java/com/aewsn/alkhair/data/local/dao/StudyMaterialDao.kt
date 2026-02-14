package com.aewsn.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aewsn.alkhair.data.models.StudyMaterial
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyMaterialDao {

    @Query("SELECT * FROM study_materials ORDER BY updatedAt DESC")
    fun getAllStudyMaterials(): Flow<List<StudyMaterial>>

    @Query("SELECT * FROM study_materials WHERE classId = :classId ORDER BY updatedAt DESC")
    fun getStudyMaterialsByClass(classId: String): Flow<List<StudyMaterial>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyMaterial(item: StudyMaterial)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyMaterialList(list: List<StudyMaterial>)

    @Query("DELETE FROM study_materials WHERE id = :id")
    suspend fun deleteStudyMaterialById(id: String)

    @Query("DELETE FROM study_materials")
    suspend fun clearAllStudyMaterials()

    @Query("SELECT * FROM study_materials WHERE isSynced = 0")
    suspend fun getUnsyncedStudyMaterials(): List<StudyMaterial>

    @Query("UPDATE study_materials SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markStudyMaterialsAsSynced(ids: List<String>)
}
