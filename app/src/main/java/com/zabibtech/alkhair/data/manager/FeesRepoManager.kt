package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.local.local_repos.LocalFeesRepository
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.remote.firebase.FirebaseFeesRepository
import com.zabibtech.alkhair.utils.StaleDetector
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeesRepoManager @Inject constructor(
    private val localFeesRepo: LocalFeesRepository, // Ab yeh class hai
    private val firebaseFeesRepo: FirebaseFeesRepository
) {

    suspend fun saveFee(feesModel: FeesModel): Result<FeesModel> {
        val result = firebaseFeesRepo.saveFee(feesModel)
        result.onSuccess { savedFee ->
            try {
                localFeesRepo.insertFee(savedFee) // Method name updated
            } catch (e: Exception) {
                Log.e("FeesRepoManager", "Failed to cache fees locally: ${savedFee.id}", e)
            }
        }
        return result
    }

    suspend fun getFee(feeId: String): Result<FeesModel> {
        val localData = try {
            localFeesRepo.getFeeById(feeId).first() // Flow se first() call kiya
        } catch (e: Exception) {
            Log.w("FeesRepoManager", "Could not get local fee data for ID: $feeId", e)
            null
        }

        if (localData != null && !StaleDetector.isStale(localData.updatedAt)) {
            return Result.success(localData)
        }

        val remoteResult = firebaseFeesRepo.getFee(feeId)
        return remoteResult.fold(
            onSuccess = { feesModel ->
                try {
                    localFeesRepo.insertFee(feesModel) // Method name updated
                } catch (e: Exception) {
                    Log.e("FeesRepoManager", "Failed to refresh fees cache from remote for ID: $feeId", e)
                }
                Result.success(feesModel)
            },
            onFailure = { exception ->
                if (localData != null) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getAllFees(): Result<List<FeesModel>> {
        val localData = try {
            localFeesRepo.getAllFees().first()
        } catch (e: Exception) {
            Log.w("FeesRepoManager", "Could not get all local fees data", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseFeesRepo.getAllFees()
        return remoteResult.fold(
            onSuccess = { feesList ->
                try {
                    localFeesRepo.clearAll() // Method name updated
                    localFeesRepo.insertFees(feesList)
                } catch (e: Exception) {
                    Log.e("FeesRepoManager", "Failed to refresh all fees cache from remote", e)
                }
                Result.success(feesList)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getFeesForStudent(studentId: String): Result<List<FeesModel>> {
        val localData = try {
            localFeesRepo.getFeesByStudentId(studentId).first() // Method name updated
        } catch (e: Exception) {
            Log.w("FeesRepoManager", "Could not get local fees for student $studentId", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseFeesRepo.getFeesForStudent(studentId)
        return remoteResult.fold(
            onSuccess = { feesList ->
                try {
                    localFeesRepo.insertFees(feesList) // Assuming this will replace existing or handle conflicts
                } catch (e: Exception) {
                    Log.e("FeesRepoManager", "Failed to refresh fees for student $studentId cache from remote", e)
                }
                Result.success(feesList)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun getFeesForMonthYear(monthYear: String): Result<List<FeesModel>> {
        val localData = try {
            // LocalFeesRepository does not have getFeesForMonthYear directly
            localFeesRepo.getAllFees().first().filter { it.monthYear == monthYear }
        } catch (e: Exception) {
            Log.w("FeesRepoManager", "Could not get local fees for month/year $monthYear", e)
            emptyList()
        }

        if (localData.isNotEmpty() && localData.all { !StaleDetector.isStale(it.updatedAt) }) {
            return Result.success(localData)
        }

        val remoteResult = firebaseFeesRepo.getFeesForMonthYear(monthYear)
        return remoteResult.fold(
            onSuccess = { feesList ->
                try {
                    localFeesRepo.insertFees(feesList) // Assuming this will replace existing or handle conflicts
                } catch (e: Exception) {
                    Log.e("FeesRepoManager", "Failed to refresh fees for month/year $monthYear cache from remote", e)
                }
                Result.success(feesList)
            },
            onFailure = { exception ->
                if (localData.isNotEmpty()) Result.success(localData) else Result.failure(exception)
            }
        )
    }

    suspend fun updateFee(feeId: String, updatedData: Map<String, Any>): Result<Unit> {
        val result = firebaseFeesRepo.updateFee(feeId, updatedData)
        result.onSuccess { _ ->
            try {
                // Fetch updated fee to save it correctly in local cache
                firebaseFeesRepo.getFee(feeId).onSuccess { updatedFee ->
                    localFeesRepo.insertFee(updatedFee) // Method name updated
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
            } catch (e: Exception) {
                Log.e("FeesRepoManager", "Failed to delete local fee: $feeId", e)
            }
        }
        return result
    }
}
