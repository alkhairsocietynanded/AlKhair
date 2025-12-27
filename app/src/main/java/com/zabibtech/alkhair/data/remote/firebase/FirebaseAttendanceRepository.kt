package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.utils.FirebaseRefs.attendanceRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAttendanceRepository @Inject constructor() {

    /**
     * ✅ SAVE ATTENDANCE (With Composite Key Optimization)
     * यह फंक्शन अटेंडेंस सेव करता है और साथ ही एक स्पेशल फील्ड "student_sync_key" जोड़ता है।
     * format: "{studentId}_{updatedAt}"
     * इससे हम बाद में स्टूडेंट और टाइम दोनों के आधार पर एक साथ फिल्टर कर सकते हैं।
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
                // 1. Root Key (Flat Structure ke liye)
                val key = "${classId}_${date}_${uid}"

                // 2. Attendance Object (Local DB aur Data ke liye)
                val attendance = Attendance(
                    studentId = uid,
                    classId = classId,
                    date = date,
                    status = status,
                    updatedAt = currentTime
                )

                // 3. Convert to Map for Firebase
                // Hum Object ko Map me badal rahe hain taaki "student_sync_key" field jod sakein
                // bina Local Room Model (Attendance.kt) ko ganda kiye.
                val firebaseData = mapOf(
                    "studentId" to attendance.studentId,
                    "classId" to attendance.classId,
                    "date" to attendance.date,
                    "status" to attendance.status,
                    "updatedAt" to attendance.updatedAt,

                    // 🔥 JADU YAHAN HAI (Composite Key)
                    // Format: studentId + "_" + timestamp
                    "student_sync_key" to "${uid}_${currentTime}",

                    // ✅ NEW KEY FOR TEACHER/CLASS
                    // Format: classId + "_" + timestamp
                    "class_sync_key" to "${classId}_${currentTime}"
                )

                updates[key] = firebaseData
            }

            if (updates.isEmpty()) {
                return Result.success(Unit)
            }

            // 4. Bulk Update
            attendanceRef.updateChildren(updates).await()
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error saving attendance", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ STUDENT SYNC (Highly Optimized)
     * यह सिर्फ उस स्टूडेंट का डेटा लाता है जो पिछले सिंक के बाद अपडेट हुआ है।
     * Query: student_sync_key (StudentId + Timestamp)
     */
    suspend fun getAttendanceForStudentUpdatedAfter(studentId: String, timestamp: Long): Result<List<Attendance>> {
        return try {
            // Start: "studentID_LastSyncTime"
            // (Timestamp + 1 taaki duplicacy na ho)
            val startKey = "${studentId}_${timestamp + 1}"

            // End: "studentID_Future"
            // (Bahut bada number taaki future ke sare records cover ho jayein)
            val endKey = "${studentId}_9999999999999"

            val snapshot = attendanceRef
                .orderByChild("student_sync_key") // ✅ Composite Index use ho raha hai
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val list = snapshot.children.mapNotNull {
                // Firebase se data lekar normal Attendance object me convert karein
                it.getValue(Attendance::class.java)
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error fetching student attendance", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ ADMIN/TEACHER SYNC (Global Sync)
     * यह पूरी स्कूल का अपडेटेड डेटा लाता है।
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
     * ✅ TEACHER SYNC (Class-Wise Optimization)
     * यह सिर्फ उस क्लास का डेटा लाएगा जो अपडेट हुआ है।
     */
    suspend fun getAttendanceForClassUpdatedAfter(classId: String, timestamp: Long): Result<List<Attendance>> {
        return try {
            // Start: "ClassID_LastSyncTime"
            val startKey = "${classId}_${timestamp + 1}"

            // End: "ClassID_Future"
            val endKey = "${classId}_9999999999999"

            val snapshot = attendanceRef
                .orderByChild("class_sync_key") // ✅ Is nayi key par query karein
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val list = snapshot.children.mapNotNull { it.getValue(Attendance::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseAttendanceRepo", "Error fetching class attendance", e)
            Result.failure(e)
        }
    }
    /**
     * 🔽 LEGACY / DIRECT UI METHODS
     * (Agar kabhi direct check karna ho)
     */
    suspend fun getAttendanceForClass(classId: String, date: String): Result<Map<String, String>> {
        return try {
            val startKey = "${classId}_${date}_"
            val endKey = "${classId}_${date}_\uf8ff"

            val snapshot = attendanceRef.orderByKey().startAt(startKey).endAt(endKey).get().await()
            val result = mutableMapOf<String, String>()

            snapshot.children.forEach { child ->
                val attendance = child.getValue(Attendance::class.java)
                if (attendance != null) {
                    result[attendance.studentId] = attendance.status
                }
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAttendanceForDateRange(startDate: String, endDate: String): Result<List<Attendance>> {
        return try {
            // Is query ke liye 'date' par index hona chahiye
            val snapshot = attendanceRef.orderByChild("date").startAt(startDate).endAt(endDate).get().await()
            val list = snapshot.children.mapNotNull { it.getValue(Attendance::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}