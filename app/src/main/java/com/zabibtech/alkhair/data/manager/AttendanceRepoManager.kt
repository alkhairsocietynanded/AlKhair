package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalAttendanceRepository
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAttendanceRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
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

    // CORRECTED: This is now a Simple Getter
    suspend fun getAttendanceForClassOnDate(classId: String, date: String): Result<List<Attendance>> {
        return try {
            val localData = localAttendanceRepo.getAttendanceByDate(date).first().filter { it.classId == classId }
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("AttendanceRepoManager", "Could not get local attendance for class $classId on $date", e)
            Result.failure(e)
        }
    }

    // CORRECTED: This is now a Simple Getter
    suspend fun getAttendanceForDate(date: String): Result<List<Attendance>> {
        return try {
            val localData = localAttendanceRepo.getAttendanceByDate(date).first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("AttendanceRepoManager", "Could not get local attendance for date $date", e)
            Result.failure(e)
        }
    }

    // CORRECTED: This is now a Simple Getter
    suspend fun getAttendanceForStudent(studentId: String): Result<List<Attendance>> {
        return try {
            val localData = localAttendanceRepo.getAttendanceByStudent(studentId).first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("AttendanceRepoManager", "Could not get local attendance for student $studentId", e)
            Result.failure(e)
        }
    }

    suspend fun syncAttendanceRange(startDate: String, endDate: String) {
        firebaseAttendanceRepo.getAttendanceForDateRange(startDate, endDate).onSuccess { list ->
            if (list.isNotEmpty()) {
                try {
                    // CORRECTED: Mark synced data as fresh
                    val updatedList = list.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localAttendanceRepo.insertAttendanceList(updatedList)
                    Log.d("AttendanceRepoManager", "Synced ${list.size} attendance records for range $startDate - $endDate")
                } catch (e: Exception) {
                    Log.e("AttendanceRepoManager", "Failed to cache synced attendance range", e)
                }
            }
        }
    }
/*
    // ADDED: For multi-device delete sync
    suspend fun deleteAttendance(attendance: Attendance): Result<Unit> {
        // Note: Deleting attendance from Firebase is complex due to denormalization.
        // A proper implementation would require a Cloud Function to handle multi-path deletion.
        // For now, we will just log a deleted record and remove it locally.
        val result = Result.success(Unit) // Placeholder for remote deletion
        result.onSuccess {
            try {
                localAttendanceRepo.deleteAttendance(attendance)

                // Create a unique ID for the deleted record
                val deletedRecordId = "${attendance.studentId}_${attendance.classId}_${attendance.date}"
                val deletedRecord = DeletedRecord(id = deletedRecordId, type = "attendance", timestamp = System.currentTimeMillis())
                FirebaseRefs.deletedRecordsRef.child(deletedRecordId).setValue(deletedRecord).await()

            } catch (e: Exception) {
                Log.e("AttendanceRepoManager", "Failed to process attendance deletion", e)
                return Result.failure(e)
            }
        }
        return result
    }

    // ADDED: For handling sync deletions
    suspend fun deleteAttendanceLocally(studentId: String, classId: String, date: String) {
        try {
            val attendance = Attendance(studentId, classId, date)
            localAttendanceRepo.deleteAttendance(attendance)
        } catch (e: Exception) {
            Log.e("AttendanceRepoManager", "Failed to delete attendance locally", e)
        }
    }*/
}
