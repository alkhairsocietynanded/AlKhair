package com.aewsn.alkhair.data.local.local_repos

import com.aewsn.alkhair.data.local.dao.SyllabusDao
import com.aewsn.alkhair.data.models.Syllabus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSyllabusRepository @Inject constructor(
    private val syllabusDao: SyllabusDao
) {

    fun getAllSyllabus(): Flow<List<Syllabus>> =
        syllabusDao.getAllSyllabus()

    fun getSyllabusByClass(classId: String): Flow<List<Syllabus>> =
        syllabusDao.getSyllabusByClass(classId)

    suspend fun insertSyllabus(syllabus: Syllabus) =
        syllabusDao.insertSyllabus(syllabus)

    suspend fun insertSyllabusList(syllabusList: List<Syllabus>) =
        syllabusDao.insertSyllabusList(syllabusList)

    suspend fun deleteSyllabusById(id: String) =
        syllabusDao.deleteSyllabusById(id)

    suspend fun clearAll() =
        syllabusDao.clearAllSyllabus()

    suspend fun getUnsyncedSyllabus(): List<Syllabus> = syllabusDao.getUnsyncedSyllabus()

    suspend fun markSyllabusAsSynced(ids: List<String>) = syllabusDao.markSyllabusAsSynced(ids)
}
