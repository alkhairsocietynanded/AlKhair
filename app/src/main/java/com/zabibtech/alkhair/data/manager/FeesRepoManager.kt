package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalFeesRepository
import com.zabibtech.alkhair.data.models.DeletedRecord
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseFeesRepository
import com.zabibtech.alkhair.utils.FirebaseRefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeesRepoManager @Inject constructor(
    private val localFeesRepo: LocalFeesRepository,
    private val firebaseFeesRepo: FirebaseFeesRepository
) {

    suspend fun saveFee(feesModel: FeesModel): Result<FeesModel> {
        val result = firebaseFeesRepo.saveFee(feesModel)
        result.onSuccess { savedFee ->
            try {
                localFeesRepo.insertFee(savedFee.copy(updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                Log.e("FeesRepoManager", "Failed to cache fees locally: ${savedFee.id}", e)
            }
        }
        return result
    }

    suspend fun getFee(feeId: String): Result<FeesModel?> {
        // Simple Getter: Always return local data. Syncing is a separate concern.
        return try {
            val localData = localFeesRepo.getFeeById(feeId).first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("FeesRepoManager", "Could not get local fee data for ID: $feeId", e)
            Result.failure(e)
        }
    }

    suspend fun getAllFees(): Result<List<FeesModel>> {
        val localData = try {
            localFeesRepo.getAllFees().first()
        } catch (e: Exception) {
            emptyList()
        }

        if (localData.isNotEmpty()) {
            return Result.success(localData)
        }

        val remoteResult = firebaseFeesRepo.getAllFees()
        return remoteResult.fold(
            onSuccess = { feesList ->
                try {
                    val updatedList = feesList.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localFeesRepo.insertFees(updatedList)
                } catch (e: Exception) {
                    Log.e("FeesRepoManager", "Failed to cache initial fees", e)
                }
                Result.success(feesList)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun syncFees(lastSync: Long) {
        firebaseFeesRepo.getFeesUpdatedAfter(lastSync).onSuccess { fees ->
            if (fees.isNotEmpty()) {
                try {
                    val updatedList = fees.map { it.copy(updatedAt = System.currentTimeMillis()) }
                    localFeesRepo.insertFees(updatedList)
                } catch (e: Exception) {
                    Log.e("FeesRepoManager", "Failed to cache synced fees", e)
                }
            }
        }
    }

    // CORRECTED: Restored to Simple Getter
    suspend fun getFeesForStudent(studentId: String): Result<List<FeesModel>> {
        return try {
            val localData = localFeesRepo.getFeesByStudentId(studentId).first()
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("FeesRepoManager", "Error fetching local fees for student $studentId", e)
            Result.failure(e)
        }
    }

    // CORRECTED: Restored to Simple Getter
    suspend fun getFeesForMonthYear(monthYear: String): Result<List<FeesModel>> {
        return try {
            val localData = localFeesRepo.getAllFees().first().filter { it.monthYear == monthYear }
            Result.success(localData)
        } catch (e: Exception) {
            Log.e("FeesRepoManager", "Error fetching local fees for month $monthYear", e)
            Result.failure(e)
        }
    }

    // CORRECTED: More efficient update without extra network call
    suspend fun updateFee(feeId: String, updatedData: Map<String, Any>): Result<Unit> {
        val finalUpdateData = updatedData.toMutableMap()
        finalUpdateData["updatedAt"] = System.currentTimeMillis()

        val result = firebaseFeesRepo.updateFee(feeId, finalUpdateData)
        result.onSuccess { _ ->
            try {
                // We need to get the full updated model to save in local DB
                firebaseFeesRepo.getFee(feeId).onSuccess { updatedFee ->
                    localFeesRepo.insertFee(updatedFee) // This already has the latest timestamp from server
                }
            } catch (e: Exception) {
                Log.e("FeesRepoManager", "Failed to update local cache for fee ID: $feeId", e)
            }
        }
        return result
    }

    suspend fun deleteFee(feeId: String): Result<Unit> {
        val result = firebaseFeesRepo.deleteFee(feeId)
        result.onSuccess { _ ->
            try {
                localFeesRepo.deleteFee(feeId)

                val deletedRecord =
                    DeletedRecord(id = feeId, type = "fees", timestamp = System.currentTimeMillis())
                FirebaseRefs.deletedRecordsRef.child(feeId).setValue(deletedRecord).await()

            } catch (e: Exception) {
                Log.e("FeesRepoManager", "Failed to process fee deletion for ID: $feeId", e)
            }
        }
        return result
    }

    suspend fun deleteFeeLocally(id: String) {
        try {
            localFeesRepo.deleteFee(id)
        } catch (e: Exception) {
            Log.e("FeesRepoManager", "Failed to delete local fee: $id", e)
        }
    }
}
