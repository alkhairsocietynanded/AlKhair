package com.zabibtech.alkhair.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zabibtech.alkhair.data.models.SalaryModel
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalary(salary: SalaryModel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaries(salaries: List<SalaryModel>)

    @Query("SELECT * FROM salary WHERE id = :id")
    fun getSalaryById(id: String): Flow<SalaryModel?>

    @Query("SELECT * FROM salary WHERE staffId = :staffId")
    fun getSalariesByStaffId(staffId: String): Flow<List<SalaryModel>>

    @Query("SELECT * FROM salary")
    fun getAllSalaries(): Flow<List<SalaryModel>>

    @Query("SELECT * FROM salary WHERE (:staffId IS NULL OR staffId = :staffId) AND (:monthYear IS NULL OR monthYear = :monthYear)")
    fun getFilteredSalaries(staffId: String?, monthYear: String?): Flow<List<SalaryModel>>

    @Query("DELETE FROM salary WHERE id = :id")
    suspend fun deleteSalary(id: String)

    @Query("DELETE FROM salary")
    suspend fun clearAllSalaries()
}
