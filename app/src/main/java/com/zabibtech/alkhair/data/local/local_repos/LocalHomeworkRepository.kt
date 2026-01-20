package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.HomeworkDao
import com.zabibtech.alkhair.data.models.Homework
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalHomeworkRepository @Inject constructor(
    private val homeworkDao: HomeworkDao
) {

    /**
     * ✅ Smart Filtering Logic
     * यह UI (ViewModel) से आने वाले filters के आधार पर सही डेटा लौटाता है।
     */
    fun observeHomeworkFiltered(
        className: String?,
        division: String?
    ): Flow<List<Homework>> {
        return when {
            // Case 1: Class aur Division dono selected hain (Student)
            // "All" check isliye lagaya taaki agar dropdown me "All" ho to specific division filter na ho
            !className.isNullOrBlank() && !division.isNullOrBlank() && division != "All" ->
                homeworkDao.getHomeworkByClassAndDivision(className, division)

            // Case 2: Sirf Class selected hai (Teacher)
            !className.isNullOrBlank() ->
                homeworkDao.getHomeworkByClassName(className)

            // Case 3: Kuch bhi select nahi hai (Admin)
            else ->
                homeworkDao.getAllHomework()
        }
    }

    // --- Standard Wrappers ---

    fun getAllHomework(): Flow<List<Homework>> =
        homeworkDao.getAllHomework()

    suspend fun insertHomework(homework: Homework) =
        homeworkDao.insertHomework(homework)

    suspend fun insertHomeworkList(homeworkList: List<Homework>) =
        homeworkDao.insertHomeworkList(homeworkList)

    suspend fun deleteHomeworkById(homeworkId: String) =
        homeworkDao.deleteHomeworkById(homeworkId)

    suspend fun clearAll() =
        homeworkDao.clearAllHomework()

    suspend fun getUnsyncedHomework(): List<Homework> = homeworkDao.getUnsyncedHomework()

    suspend fun markHomeworkAsSynced(ids: List<String>) = homeworkDao.markHomeworkAsSynced(ids)
}