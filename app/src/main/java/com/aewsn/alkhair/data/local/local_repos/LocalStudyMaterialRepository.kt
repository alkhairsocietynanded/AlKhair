package com.aewsn.alkhair.data.local.local_repos

import com.aewsn.alkhair.data.local.dao.StudyMaterialDao
import com.aewsn.alkhair.data.models.StudyMaterial
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalStudyMaterialRepository @Inject constructor(
    private val studyMaterialDao: StudyMaterialDao
) {

    fun getAllStudyMaterials(): Flow<List<StudyMaterial>> =
        studyMaterialDao.getAllStudyMaterials()

    fun getStudyMaterialsByClass(classId: String): Flow<List<StudyMaterial>> =
        studyMaterialDao.getStudyMaterialsByClass(classId)

    suspend fun insertStudyMaterial(item: StudyMaterial) =
        studyMaterialDao.insertStudyMaterial(item)

    suspend fun insertStudyMaterialList(list: List<StudyMaterial>) =
        studyMaterialDao.insertStudyMaterialList(list)

    suspend fun deleteStudyMaterialById(id: String) =
        studyMaterialDao.deleteStudyMaterialById(id)

    suspend fun clearAll() =
        studyMaterialDao.clearAllStudyMaterials()

    suspend fun getUnsyncedStudyMaterials(): List<StudyMaterial> =
        studyMaterialDao.getUnsyncedStudyMaterials()

    suspend fun markStudyMaterialsAsSynced(ids: List<String>) =
        studyMaterialDao.markStudyMaterialsAsSynced(ids)
}
