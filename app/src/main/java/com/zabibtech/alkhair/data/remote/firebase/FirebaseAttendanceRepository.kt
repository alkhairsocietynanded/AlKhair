package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.utils.FirebaseRefs.attendanceRef
import com.zabibtech.alkhair.utils.FirebaseRefs.userAttendanceRef
import com.zabibtech.alkhair.utils.FirebaseRefs.dateAttendanceRef // Naya import add kiya hai
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.mutableMapOf

@Singleton
class FirebaseAttendanceRepository @Inject constructor() {

    suspend fun saveAttendanceForClass(
        classId: String,
        date: String,
        attendanceMap: Map<String, String>
    ): Result<Unit> {
        return try {
            if (classId.isBlank() || date.isBlank()) {
                throw IllegalArgumentException("Class ID and date cannot be blank.")
            }

            val attendanceUpdates = mutableMapOf<String, Any>()
            val userAttendanceUpdates = mutableMapOf<String, Any>()
            val dateAttendanceUpdates = mutableMapOf<String, Any>() // Naya map add kiya hai

            attendanceMap.forEach { (uid, status) ->
                // Update for attendanceRef: /attendance/{classId}/{date}/{uid}
                attendanceUpdates["$classId/$date/$uid"] = status
                // Update for userAttendanceRef: /user_attendance/{uid}/{classId}/{date}
                userAttendanceUpdates["$uid/$classId/$date"] = status
                // Update for dateAttendanceRef: /date_attendance/{date}/{classId}/{uid}
                dateAttendanceUpdates["$date/$classId/$uid"] = status // Naya update add kiya hai
            }

            if (attendanceUpdates.isEmpty()) {
                return Result.success(Unit) // Nothing to update
            }

            // Perform multi-path update for attendanceRef
            attendanceRef.updateChildren(attendanceUpdates).await()

            // Perform multi-path update for userAttendanceRef
            if (userAttendanceUpdates.isNotEmpty()) {
                userAttendanceRef.updateChildren(userAttendanceUpdates).await()
            }

            // Perform multi-path update for dateAttendanceRef
            if (dateAttendanceUpdates.isNotEmpty()) {
                dateAttendanceRef.updateChildren(dateAttendanceUpdates).await() // Naya update perform kiya hai
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error saving attendance for class $classId on $date", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceForClass(classId: String, date: String): Result<Map<String, String>> {
        return try {
            if (classId.isBlank() || date.isBlank()) {
                throw IllegalArgumentException("Class ID and date cannot be blank.")
            }
            val snapshot = attendanceRef.child(classId).child(date).get().await()
            val result = mutableMapOf<String, String>()
            if (snapshot.exists()) {
                snapshot.children.forEach { child ->
                    val uid = child.key
                    val status = child.getValue(String::class.java)
                    if (uid != null && status != null) {
                        result[uid] = status
                    }
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error getting attendance for class $classId on $date", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceForUser(userId: String): Result<Map<String, Map<String, String>>> {
        return try {
            if (userId.isBlank()) {
                throw IllegalArgumentException("User ID cannot be blank.")
            }
            // Efficient Query: Directly query user_attendance for the specific user
            val snapshot = userAttendanceRef.child(userId).get().await()
            val result = mutableMapOf<String, MutableMap<String, String>>()

            if (snapshot.exists()) {
                snapshot.children.forEach { classSnapshot -> // Each child is a classId
                    val classId = classSnapshot.key
                    if (classId != null) {
                        classSnapshot.children.forEach { dateSnapshot -> // Each child is a date
                            val date = dateSnapshot.key
                            val status = dateSnapshot.getValue(String::class.java)
                            if (date != null && status != null) {
                                val dateMap = result.getOrPut(classId) { mutableMapOf() }
                                dateMap[date] = status
                            }
                        }
                    }
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error getting attendance for user $userId", e)
            Result.failure(e)
        }
    }

    suspend fun getAttendanceForDate(date: String): Result<Map<String, Map<String, String>>> {
        return try {
            if (date.isBlank()) {
                throw IllegalArgumentException("Date cannot be blank.")
            }
            // Efficient Query: Directly query date_attendance for the specific date
            val snapshot = dateAttendanceRef.child(date).get().await() // Optimized query
            val result = mutableMapOf<String, MutableMap<String, String>>()
            if (snapshot.exists()) {
                snapshot.children.forEach { classSnapshot -> // Each child is a classId
                    val classId = classSnapshot.key
                    if (classId != null) {
                        val attendanceData = mutableMapOf<String, String>()
                        classSnapshot.children.forEach { userSnapshot -> // Each child is a uid
                            val uid = userSnapshot.key
                            val status = userSnapshot.getValue(String::class.java)
                            if (uid != null && status != null) {
                                attendanceData[uid] = status
                            }
                        }
                        if (attendanceData.isNotEmpty()){
                            result[classId] = attendanceData
                        }
                    }
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error getting attendance for date $date", e)
            Result.failure(e)
        }
    }
}