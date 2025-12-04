package com.zabibtech.alkhair.data.local.local_repos

import com.zabibtech.alkhair.data.local.dao.SalaryDao
import com.zabibtech.alkhair.data.models.SalaryModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalSalaryRepository @Inject constructor(
    private val salaryDao: SalaryDao
) {
    fun getSalaryById(id: String): Flow<SalaryModel?> = salaryDao.getSalaryById(id)

    fun getSalariesByStaffId(staffId: String): Flow<List<SalaryModel>> = 
        salaryDao.getSalariesByStaffId(staffId)

    fun getAllSalaries(): Flow<List<SalaryModel>> = salaryDao.getAllSalaries()

    suspend fun insertSalary(salary: SalaryModel) = salaryDao.insertSalary(salary)

    suspend fun insertSalaries(salaries: List<SalaryModel>) = salaryDao.insertSalaries(salaries)

    suspend fun deleteSalary(id: String) = salaryDao.deleteSalary(id)

    suspend fun clearAll() = salaryDao.clearAllSalaries()
}
