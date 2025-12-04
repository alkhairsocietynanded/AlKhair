package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.HomeworkDao
import com.zabibtech.alkhair.data.models.Homework
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalHomeworkRepository @Inject constructor(
    private val homeworkDao: HomeworkDao
) {
    fun getAllHomework(): Flow<List<Homework>> = homeworkDao.getAllHomework()

    fun getHomeworkByClass(className: String, division: String): Flow<List<Homework>> = 
        homeworkDao.getHomeworkByClass(className, division)

    suspend fun insertHomework(homework: Homework) = homeworkDao.insertHomework(homework)

    suspend fun insertHomeworkList(homeworkList: List<Homework>) = 
        homeworkDao.insertHomeworkList(homeworkList)

    suspend fun clearAll() = homeworkDao.clearAllHomework()
}
