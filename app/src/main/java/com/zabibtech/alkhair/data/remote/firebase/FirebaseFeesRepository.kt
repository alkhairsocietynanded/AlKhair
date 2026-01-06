package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.utils.FirebaseRefs.feesRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseFeesRepository @Inject constructor() {

    /**
     * ✅ SAVE FEE (With Composite Key Optimization)
     * यह function फीस सेव करता है और साथ ही "student_sync_key" भी बनाता है।
     * Format: "{studentId}_{updatedAt}"
     */
    suspend fun saveFee(feesModel: FeesModel): Result<FeesModel> {
        return try {
            val feeId = feesModel.id.ifEmpty { feesRef.push().key!! }
            val currentTime = System.currentTimeMillis()

            // Safety checks for keys
            val safeShift = feesModel.shift.ifBlank { "General" }
            val safeClassId = feesModel.classId.ifBlank { "NA" }

            // 1. Final Object for Local & Remote Return
            val newFeesModel = feesModel.copy(
                id = feeId,
                updatedAt = currentTime
            )

            // 2. Convert to Map to inject "student_sync_key"
            // Hum Object ko Map me convert kar rahe hain taaki extra field add kar sakein
            val feeMap = mapOf(
                "id" to newFeesModel.id,
                "studentId" to newFeesModel.studentId,
                "studentName" to newFeesModel.studentName,
                "classId" to newFeesModel.classId,
                "shift" to newFeesModel.shift,
                "monthYear" to newFeesModel.monthYear,
                "baseAmount" to newFeesModel.baseAmount,
                "paidAmount" to newFeesModel.paidAmount,
                "dueAmount" to newFeesModel.dueAmount,
                "discounts" to newFeesModel.discounts,
                "netFees" to newFeesModel.netFees,
                "paymentDate" to newFeesModel.paymentDate,
                "remarks" to newFeesModel.remarks,
                "paymentStatus" to newFeesModel.paymentStatus,
                "updatedAt" to newFeesModel.updatedAt,

                // 🔥 OPTIMIZATION KEY: StudentID + Timestamp
                // Isse hum efficiently sirf specific student ka naya data fetch kar payenge
                // 🔥 EXISTING KEY (Student ke liye)
                "student_sync_key" to "${newFeesModel.studentId}_$currentTime",

                // ✅ NEW KEY: ClassID + Shift + Timestamp
                "class_shift_sync_key" to "${safeClassId}_${safeShift}_$currentTime"
            )

            feesRef.child(feeId).setValue(feeMap).await()
            Result.success(newFeesModel)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error saving fee", e)
            Result.failure(e)
        }
    }

    /**
     * ✅ UPDATE FEE
     * Update karte waqt bhi Timestamp aur Sync Key update karna zaroori hai.
     */
    suspend fun updateFee(feeId: String, updatedData: Map<String, Any>): Result<Unit> {
        return try {
            val dataToUpdate = updatedData.toMutableMap()
            val currentTime = System.currentTimeMillis()

            // 1. Update Timestamp
            dataToUpdate["updatedAt"] = currentTime

            // 2. Update Composite Key (student_sync_key)
            // FeesRepoManager se humein 'studentId' map me mil raha hai.
            if (dataToUpdate.containsKey("studentId")) {
                val sId = dataToUpdate["studentId"] as String
                dataToUpdate["student_sync_key"] = "${sId}_$currentTime"
            }
            // ✅ Update Class+Shift Key
            if (dataToUpdate.containsKey("classId") && dataToUpdate.containsKey("shift")) {
                val cId = dataToUpdate["classId"] as String
                val shift = dataToUpdate["shift"] as String
                val safeShift = shift.ifBlank { "General" }
                dataToUpdate["class_shift_sync_key"] = "${cId}_${safeShift}_$currentTime"
            }

            feesRef.child(feeId).updateChildren(dataToUpdate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error updating fee", e)
            Result.failure(e)
        }
    }

    // ✅ TEACHER SYNC (Class + Shift Optimized)
    suspend fun getFeesForClassAndShiftUpdatedAfter(
        classId: String,
        shift: String,
        timestamp: Long
    ): Result<List<FeesModel>> {
        return try {
            val safeShift = shift.ifBlank { "General" }

            val startKey = "${classId}_${safeShift}_${timestamp + 1}"
            val endKey = "${classId}_${safeShift}_9999999999999"

            val snapshot = feesRef
                .orderByChild("class_shift_sync_key") // ✅ Use New Index
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            // ✅ Force Key as ID fix included
            val list = snapshot.children.mapNotNull { child ->
                child.getValue(FeesModel::class.java)?.copy(id = child.key!!)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ STUDENT SYNC (Highly Optimized)
     * यह सिर्फ उस स्टूडेंट का डेटा लाता है जो LastSync के बाद बदला है।
     * 0 Bandwidth Wastage!
     */
    suspend fun getFeesForStudentUpdatedAfter(
        studentId: String,
        timestamp: Long
    ): Result<List<FeesModel>> {
        return try {
            // Start: "Student_LastSync"
            val startKey = "${studentId}_${timestamp + 1}"

            // End: "Student_Future"
            val endKey = "${studentId}_9999999999999"

            val snapshot = feesRef
                .orderByChild("student_sync_key") // ✅ Index required in Firebase Rules
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val fees = snapshot.children.mapNotNull {
                it.getValue(FeesModel::class.java)?.copy(id = it.key!!)
            }
            Result.success(fees)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error fetching student fees sync", e)
            Result.failure(e)
        }
    }

    // ✅ TEACHER SYNC FUNCTION
    suspend fun getFeesForClassUpdatedAfter(
        classId: String,
        timestamp: Long
    ): Result<List<FeesModel>> {
        return try {
            val startKey = "${classId}_${timestamp + 1}"
            val endKey = "${classId}_9999999999999"

            val snapshot = feesRef
                .orderByChild("class_sync_key") // ✅ Index Required
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val list = snapshot.children.mapNotNull {
                it.getValue(FeesModel::class.java)?.copy(id = it.key!!)
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ✅ ADMIN SYNC (Global)
     * यह पूरे स्कूल का डेटा लाता है जो बदला है।
     */
    suspend fun getFeesUpdatedAfter(timestamp: Long): Result<List<FeesModel>> {
        return try {
            val snapshot = feesRef
                .orderByChild("updatedAt")
                .startAt((timestamp + 1).toDouble())
                .get()
                .await()

            val fees = snapshot.children.mapNotNull {
                it.getValue(FeesModel::class.java)?.copy(id = it.key!!)
            }
            Result.success(fees)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error getting updated fees global", e)
            Result.failure(e)
        }
    }

    // --- Standard Methods ---

    suspend fun getFeesForMonthYear(monthYear: String): Result<List<FeesModel>> {
        return try {
            val query = feesRef.orderByChild("monthYear").equalTo(monthYear)
            val snapshot = query.get().await()
            val fees = snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
            Result.success(fees)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error getting fees for month $monthYear", e)
            Result.failure(e)
        }
    }

    suspend fun getFee(feeId: String): Result<FeesModel> {
        return try {
            val snapshot = feesRef.child(feeId).get().await()
            val feesModel = snapshot.getValue(FeesModel::class.java)
            if (feesModel != null) {
                Result.success(feesModel)
            } else {
                Result.failure(NoSuchElementException("Fee not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFee(feeId: String): Result<Unit> {
        return try {
            feesRef.child(feeId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Legacy/Backup method
    suspend fun getFeesForStudent(studentId: String): Result<List<FeesModel>> {
        return try {
            val query = feesRef.orderByChild("studentId").equalTo(studentId)
            val snapshot = query.get().await()
            val fees = snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
            Result.success(fees)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}