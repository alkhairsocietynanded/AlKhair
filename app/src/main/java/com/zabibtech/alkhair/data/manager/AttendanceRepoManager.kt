package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalAttendanceRepository
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAttendanceRepository
import com.zabibtech.alkhair.utils.StaleDetector
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepoManager @Inject constructor(
    private val localAttendanceRepo: LocalAttendanceRepository,
    private val firebaseAttendanceRepo: FirebaseAttendanceRepository
) {

    suspend fun saveAttendance(
        classId: String,
        date: String,
        attendanceMap: Map<String, String>
    ): Result<Unit> {
        val result = firebaseAttendanceRepo.saveAttendanceForClass(classId, date, attendanceMap)
        result.onSuccess { _ ->
            try {
                if (attendanceMap.isNotEmpty()) {
                    val attendanceList = attendanceMap.map { (studentId, status) ->
                        Attendance(
                            studentId = studentId,
                            classId = classId,
                            date = date,
                            status = status,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    localAttendanceRepo.insertAttendanceList(attendanceList)
                }
            } catch (e: Exception) {
                Log.e("AttendanceRepoManager", "Failed to cache attendance locally", e)
            }
        }
        return result
    }

    suspend fun getAttendanceForClassOnDate(classId: String, date: String): Result<List<Attendance>> {
        val localData = try {
            localAttendanceRepo.getAttendanceByDate(date).first().filter { it.classId == classId }
        } catch (e: Exception) {
            Log.w("AttendanceRepoManager", "Could not get local attendance data", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseAttendanceRepo.getAttendanceForClass(classId, date)
        return remoteResult.fold(
            onSuccess = { attendanceMap ->
                val attendanceList = attendanceMap.map { (studentId, status) ->
                    Attendance(
                        studentId = studentId,
                        classId = classId,
                        date = date,
                        status = status,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                try {
                    if (attendanceList.isNotEmpty()) {
                        localAttendanceRepo.insertAttendanceList(attendanceList)
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceRepoManager", "Failed to refresh attendance cache from remote", e)
                }
                Result.success(attendanceList)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getAttendanceForDate(date: String): Result<List<Attendance>> {
        val localData = try {
            localAttendanceRepo.getAttendanceByDate(date).first()
        } catch (e: Exception) {
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseAttendanceRepo.getAttendanceForDate(date)
        return remoteResult.fold(
            onSuccess = { remoteMap ->
                val attendanceList = mutableListOf<Attendance>()
                remoteMap.forEach { (classId, userStatusMap) ->
                    userStatusMap.forEach { (studentId, status) ->
                        attendanceList.add(
                            Attendance(
                                studentId = studentId,
                                classId = classId,
                                date = date,
                                status = status,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                try {
                    if (attendanceList.isNotEmpty()) {
                        localAttendanceRepo.insertAttendanceList(attendanceList)
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceRepoManager", "Failed to cache attendance for date $date", e)
                }
                Result.success(attendanceList)
            },
            onFailure = { if (localData.isNotEmpty()) Result.success(localData) else Result.failure(it) }
        )
    }

    suspend fun getAttendanceForStudent(studentId: String): Result<List<Attendance>> {
        val localData = try {
            localAttendanceRepo.getAttendanceByStudent(studentId).first()
        } catch (e: Exception) {
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseAttendanceRepo.getAttendanceForUser(studentId)
        return remoteResult.fold(
            onSuccess = { remoteMap ->
                val attendanceList = mutableListOf<Attendance>()
                remoteMap.forEach { (classId, dateStatusMap) ->
                    dateStatusMap.forEach { (date, status) ->
                        attendanceList.add(
                            Attendance(
                                studentId = studentId,
                                classId = classId,
                                date = date,
                                status = status,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                try {
                    if (attendanceList.isNotEmpty()) {
                        localAttendanceRepo.insertAttendanceList(attendanceList)
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceRepoManager", "Failed to cache attendance for student $studentId", e)
                }
                Result.success(attendanceList)
            },
            onFailure = { if (localData.isNotEmpty()) Result.success(localData) else Result.failure(it) }
        )
    }
}