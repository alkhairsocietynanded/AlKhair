package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.utils.FirebaseRefs.divisionsRef
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseDivisionRepository @Inject constructor() {

    suspend fun addDivision(division: DivisionModel): Result<DivisionModel> {
        return try {
            val key = division.id.ifEmpty { divisionsRef.push().key ?: throw Exception("Failed to generate division key") }
            val newDivision = division.copy(id = key)
            divisionsRef.child(key).setValue(newDivision).await()
            Result.success(newDivision)
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error adding division", e)
            Result.failure(e)
        }
    }

    suspend fun updateDivision(division: DivisionModel): Result<Unit> {
        return try {
            if (division.id.isEmpty()) throw Exception("Invalid division id for update")
            divisionsRef.child(division.id).setValue(division).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error updating division", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDivision(divisionId: String): Result<Unit> {
        return try {
            if (divisionId.isEmpty()) throw Exception("Invalid division id for delete")
            divisionsRef.child(divisionId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error deleting division", e)
            Result.failure(e)
        }
    }

    suspend fun getAllDivisions(): Result<List<DivisionModel>> {
        return try {
            val snap = divisionsRef.get().await()
            val divisions = snap.children.mapNotNull { it.getValue(DivisionModel::class.java) }
            Result.success(divisions)
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error getting all divisions", e)
            Result.failure(e)
        }
    }

    suspend fun doesDivisionExist(divisionName: String): Result<Boolean> {
        return try {
            val snap = divisionsRef.orderByChild("name")
                .equalTo(divisionName)
                .limitToFirst(1)
                .get().await()
            Result.success(snap.exists())
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error checking if division exists: $divisionName", e)
            Result.failure(e)
        }
    }

    suspend fun getDivisionsUpdatedAfter(timestamp: Long): Result<List<DivisionModel>> {
        return try {
            val snapshot = divisionsRef.orderByChild("updatedAt").startAt(timestamp.toDouble()).get().await()
            val divisions = snapshot.children.mapNotNull { it.getValue(DivisionModel::class.java) }
            Result.success(divisions)
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error getting updated divisions", e)
            Result.failure(e)
        }
    }
    suspend fun saveDivisionBatch(divisionList: List<DivisionModel>): Result<Unit> {
        return try {
            if (divisionList.isEmpty()) return Result.success(Unit)

            val updates = mutableMapOf<String, Any>()
            val currentTime = System.currentTimeMillis()

            divisionList.forEach { division ->
                val newDivision = division.copy(updatedAt = currentTime)
                updates[newDivision.id] = newDivision
            }

            divisionsRef.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error saving batch division", e)
            Result.failure(e)
        }
    }

    suspend fun deleteDivisionBatch(ids: List<String>): Result<Unit> {
        return try {
            if (ids.isEmpty()) return Result.success(Unit)

            val updates = mutableMapOf<String, Any?>()
            val currentTime = System.currentTimeMillis()
            val rootRef = divisionsRef.root

            ids.forEach { id ->
                updates["divisions/$id"] = null
                val tombstone = mapOf(
                    "id" to id,
                    "type" to "division",
                    "deletedAt" to currentTime
                )
                updates["deleted_records/$id"] = tombstone
            }

            rootRef.updateChildren(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseDivisionRepo", "Error deleting batch division", e)
            Result.failure(e)
        }
    }
}