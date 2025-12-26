package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.utils.FirebaseRefs.attendanceRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAttendanceRepository @Inject constructor() {

    // ... (Save logic wahi rahega jo aapne bheja tha) ...

    /**
     * ✅ SAVE (Flat Structure)
     * Saves data with composite key "classId_date_studentId"
     */
    suspend fun saveAttendanceForClass(
        classId: String,
        date: String,
        attendanceMap: Map<String, String>
    ): Result<Unit> {
        return try {
            if (classId.isBlank() || date.isBlank()) {
                throw IllegalArgumentException("Class ID and date cannot be blank.")
            }

            val updates = mutableMapOf<String, Any>()
            val currentTime = System.currentTimeMillis()

            attendanceMap.forEach { (uid, status) ->
                val key = "${classId}_${date}_${uid}"
                val attendance = Attendance(
                    studentId = uid,
                    classId = classId,
                    date = date,
                    status = status,
                    updatedAt = currentTime
                )
                updates[key] = attendance
            }

            if (updates.isEmpty()) return Result.success(Unit)

            attendanceRef.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error saving attendance", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ ADMIN/TEACHER SYNC (Global Sync)
     * यह पूरी स्कूल/क्लास का बदला हुआ डेटा लाता है।
     * Use Case: Admin Dashboard
     */
    suspend fun getAttendanceUpdatedAfter(timestamp: Long): Result<List<Attendance>> {
        return try {
            val snapshot = attendanceRef
                .orderByChild("updatedAt")
                .startAt((timestamp + 1).toDouble())
                .get()
                .await()

            val list = snapshot.children.mapNotNull { it.getValue(Attendance::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error fetching updated attendance (Global)", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ STUDENT SYNC (Targeted Sync) - **NEW FUNCTION**
     * यह सिर्फ एक स्पेसिफिक स्टूडेंट का डेटा लाता है।
     * Use Case: Student Dashboard (Security & Performance)
     */
    suspend fun getAttendanceForStudentUpdatedAfter(studentId: String, timestamp: Long): Result<List<Attendance>> {
        return try {
            // 1. Firebase se sirf is Student ka data mangwao (Server-Side Filter)
            // Note: Iske liye "studentId" par Index hona zaroori hai
            val snapshot = attendanceRef
                .orderByChild("studentId")
                .equalTo(studentId)
                .get()
                .await()

            // 2. Timestamp check Client-Side karo
            // (Firebase RDB me 2 field par query ek sath nahi ho sakti)
            val list = snapshot.children.mapNotNull { it.getValue(Attendance::class.java) }
                .filter { it.updatedAt > timestamp }

            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error fetching student attendance", e)
            Result.failure(e)
        }
    }

    // ... (Baaki supporting methods jaise getAttendanceForClass wahi rahenge) ...

    suspend fun getAttendanceForClass(classId: String, date: String): Result<Map<String, String>> {
        return try {
            val startKey = "${classId}_${date}_"
            val endKey = "${classId}_${date}_\uf8ff"

            val snapshot = attendanceRef.orderByKey().startAt(startKey).endAt(endKey).get().await()
            val result = mutableMapOf<String, String>()
            snapshot.children.forEach { child ->
                val attendance = child.getValue(Attendance::class.java)
                if (attendance != null) result[attendance.studentId] = attendance.status
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttendanceForDateRange(startDate: String, endDate: String): Result<List<Attendance>> {
        return try {
            // Note: Flat structure me Date query ke liye 'date' field par index zaroori hai
            val snapshot = attendanceRef.orderByChild("date").startAt(startDate).endAt(endDate).get().await()
            val list = snapshot.children.mapNotNull { it.getValue(Attendance::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}