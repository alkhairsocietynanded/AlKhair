package com.aewsn.alkhair.data.local.local_repos

import com.aewsn.alkhair.data.local.dao.LeaveDao
import com.aewsn.alkhair.data.models.Leave
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LocalLeaveRepository @Inject constructor(
    private val leaveDao: LeaveDao
) {
    fun getLeavesByStudent(studentId: String): Flow<List<Leave>> = leaveDao.getLeavesByStudent(studentId)

    suspend fun insertLeave(leave: Leave) = leaveDao.insertLeave(leave)

    suspend fun insertLeaves(leaves: List<Leave>) = leaveDao.insertLeaves(leaves)

    suspend fun updateLeave(leave: Leave) = leaveDao.updateLeave(leave)

    suspend fun deleteLeave(id: String) = leaveDao.deleteLeaveById(id)

    suspend fun getLeaveById(id: String): Leave? = leaveDao.getLeaveById(id)

    fun getUnsyncedLeaves() = leaveDao.getUnsyncedLeaves()

    fun getAllLeaves() = leaveDao.getAllLeaves()
    
    fun getLeavesByClass(classId: String) = leaveDao.getLeavesByClass(classId)

    suspend fun clearLeaves() = leaveDao.clearLeaves()
}
