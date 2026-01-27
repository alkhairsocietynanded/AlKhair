package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.ClassDao
import com.zabibtech.alkhair.data.models.ClassModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalClassRepository @Inject constructor(
    private val classDao: ClassDao
) {
    fun getAllClasses(): Flow<List<ClassModel>> = classDao.getAllClasses()
    suspend fun getAllClassesOneShot(): List<ClassModel> = classDao.getAllClassesOneShot()

    suspend fun getClassById(classId: String): ClassModel? = classDao.getClassById(classId)

    suspend fun insertClass(classModel: ClassModel) = classDao.insertClass(classModel)

    suspend fun insertClasses(classes: List<ClassModel>) = classDao.insertClasses(classes)

    suspend fun deleteClass(classId: String) = classDao.deleteClass(classId)

    suspend fun clearAll() = classDao.clearAllClasses()

    suspend fun getUnsyncedClasses(): List<ClassModel> = classDao.getUnsyncedClasses()

    suspend fun markClassesAsSynced(ids: List<String>) = classDao.markClassesAsSynced(ids)
}
