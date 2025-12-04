package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.DivisionDao
import com.zabibtech.alkhair.data.models.DivisionModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDivisionRepository @Inject constructor(
    private val divisionDao: DivisionDao
) {
    fun getAllDivisions(): Flow<List<DivisionModel>> = divisionDao.getAllDivisions()

    suspend fun insertDivision(division: DivisionModel) = divisionDao.insertDivision(division)

    suspend fun insertDivisions(divisions: List<DivisionModel>) =
        divisionDao.insertDivisions(divisions)

    suspend fun deleteDivision(divisionId: String) = divisionDao.deleteDivision(divisionId)

    suspend fun clearAll() = divisionDao.clearAllDivisions()
}
