package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.FeesDao
import com.zabibtech.alkhair.data.models.FeesModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFeesRepository @Inject constructor(
    private val feesDao: FeesDao
) {
    fun getFeeById(id: String): Flow<FeesModel?> = feesDao.getFeeById(id)

    fun getFeesByStudentId(studentId: String): Flow<List<FeesModel>> = 
        feesDao.getFeesByStudentId(studentId)

    fun getAllFees(): Flow<List<FeesModel>> = feesDao.getAllFees()

    suspend fun insertFee(fee: FeesModel) = feesDao.insertFee(fee)

    suspend fun insertFees(fees: List<FeesModel>) = feesDao.insertFees(fees)

    suspend fun deleteFee(id: String) = feesDao.deleteFee(id)

    suspend fun clearAll() = feesDao.clearAllFees()

    suspend fun getUnsyncedFees(): List<FeesModel> = feesDao.getUnsyncedFees()

    suspend fun markFeesAsSynced(ids: List<String>) = feesDao.markFeesAsSynced(ids)
}
