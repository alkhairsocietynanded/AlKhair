package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.utils.FirebaseRefs.attendanceRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor() {
    suspend fun saveAttendanceForClass(
        classId: String?,
        date: String,
        attendanceMap: Map<String, String>
    ) {
        if (classId == null) return

        val updates = mutableMapOf<String, Any>()
        attendanceMap.forEach { (uid, status) ->
            updates["$classId/$date/$uid"] = status
        }

        attendanceRef.updateChildren(updates).await()
    }

    suspend fun getAttendanceForClass(classId: String, date: String): Map<String, String> {
        val snapshot = attendanceRef.child(classId).child(date).get().await()
        val result = mutableMapOf<String, String>()
        if (snapshot.exists()) {
            snapshot.children.forEach { child ->
                val uid = child.key ?: return@forEach
                val status = child.getValue(String::class.java) ?: return@forEach
                result[uid] = status
            }
        }
        return result
    }

    suspend fun getAttendanceForUser(userId: String): Map<String, Map<String, String>> {
        val snapshot = attendanceRef.get().await()
        val result = mutableMapOf<String, MutableMap<String, String>>()

        if (snapshot.exists()) {
            snapshot.children.forEach { classSnapshot ->
                val classId = classSnapshot.key ?: return@forEach
                classSnapshot.children.forEach { dateSnapshot ->
                    val date = dateSnapshot.key ?: return@forEach
                    val status = dateSnapshot.child(userId).getValue(String::class.java)
                    if (status != null) {
                        val dateMap = result.getOrPut(classId) { mutableMapOf() }
                        dateMap[date] = status
                    }
                }
            }
        }
        return result
    }

}
