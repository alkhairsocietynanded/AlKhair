package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.utils.FirebaseRefs.feesRef
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseFeesRepository @Inject constructor() {

    suspend fun saveFee(feesModel: FeesModel): Result<FeesModel> {
        return try {
            val feeId = feesModel.id.ifEmpty { feesRef.push().key!! }
            val newFeesModel = feesModel.copy(id = feeId, updatedAt = System.currentTimeMillis())
            feesRef.child(feeId).setValue(newFeesModel).await()
            Result.success(newFeesModel)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error saving fee for student ${feesModel.studentId}", e)
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
                Result.failure(NoSuchElementException("Fee with ID $feeId not found."))
            }
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error getting fee with ID $feeId", e)
            Result.failure(e)
        }
    }

    // Get all fees (use with caution for large datasets)
    suspend fun getAllFees(): Result<List<FeesModel>> {
        return try {
            val snapshot = feesRef.get().await()
            val fees = snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
            Result.success(fees)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error getting all fees", e)
            Result.failure(e)
        }
    }

    // Get fees for a specific student
    suspend fun getFeesForStudent(studentId: String): Result<List<FeesModel>> {
        return try {
            val query = feesRef.orderByChild("studentId").equalTo(studentId)
            val snapshot = query.get().await()
            val fees = snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
            Result.success(fees)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error getting fees for student $studentId", e)
            Result.failure(e)
        }
    }

    // Get fees for a specific month and year (e.g., YYYY-MM)
    suspend fun getFeesForMonthYear(monthYear: String): Result<List<FeesModel>> {
        return try {
            val query = feesRef.orderByChild("monthYear").equalTo(monthYear)
            val snapshot = query.get().await()
            val fees = snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
            Result.success(fees)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error getting fees for month/year $monthYear", e)
            Result.failure(e)
        }
    }

    suspend fun updateFee(feeId: String, updatedData: Map<String, Any>): Result<Unit> {
        return try {
            val dataToUpdate = updatedData.toMutableMap()
            dataToUpdate["updatedAt"] = System.currentTimeMillis() // Update timestamp
            feesRef.child(feeId).updateChildren(dataToUpdate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error updating fee with ID $feeId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteFee(feeId: String): Result<Unit> {
        return try {
            feesRef.child(feeId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error deleting fee with ID $feeId", e)
            Result.failure(e)
        }
    }

    suspend fun getFeesUpdatedAfter(timestamp: Long): Result<List<FeesModel>> {
        return try {
            val snapshot = feesRef.orderByChild("updatedAt").startAt(timestamp.toDouble()).get().await()
            val fees = snapshot.children.mapNotNull { it.getValue(FeesModel::class.java) }
            Result.success(fees)
        } catch (e: Exception) {
            Log.e("FirebaseFeesRepo", "Error getting updated fees", e)
            Result.failure(e)
        }
    }
}