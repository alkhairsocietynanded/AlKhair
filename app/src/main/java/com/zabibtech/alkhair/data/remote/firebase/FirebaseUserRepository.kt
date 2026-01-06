package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.FirebaseRefs.usersDb
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserRepository @Inject constructor() {

    // ✅ CREATE (With Composite Key)
    suspend fun createUser(user: User): Result<User> {
        return try {
            if (user.uid.isBlank()) {
                return Result.failure(Exception("User ID (UID) is missing!"))
            }

            val currentTime = System.currentTimeMillis()
            val finalUser = user.copy(updatedAt = currentTime)
            // Validate Shift (Default to "General" if empty)
            val safeShift = user.shift.ifBlank { "General" }
            val safeClassId = user.classId.ifBlank { "NA" }

            // Convert to Map to inject "class_sync_key"
            // This key allows efficient querying for Teachers (Class + Time)
            val userMap = mapOf(
                "uid" to finalUser.uid,
                "name" to finalUser.name,
                "email" to finalUser.email,
                "role" to finalUser.role,
                "classId" to finalUser.classId,
                "className" to finalUser.className,
                "divisionId" to finalUser.divisionId,
                "divisionName" to finalUser.divisionName,
                "phone" to finalUser.phone,
                "address" to finalUser.address,
                "dateOfBirth" to finalUser.dateOfBirth,
                "dateOfJoining" to finalUser.dateOfJoining,
                "salary" to finalUser.salary,
                "subject" to finalUser.subject,
                "parentName" to finalUser.parentName,
                "password" to finalUser.password, // Be careful saving passwords in plain text
                "shift" to finalUser.shift,
                "totalFees" to finalUser.totalFees,
                "updatedAt" to finalUser.updatedAt,

                // ✅ NEW KEY: ClassID + Shift + Timestamp
                // Example: "-N8s..._Subah_1766500000"
                "class_shift_sync_key" to "${safeClassId}_${safeShift}_$currentTime"
            )

            usersDb.child(finalUser.uid).setValue(userMap).await()
            Result.success(finalUser)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error creating user", e)
            Result.failure(e)
        }
    }

    // ✅ UPDATE (With Composite Key)
    suspend fun updateUser(user: User): Result<User> {
        return try {
            val currentTime = System.currentTimeMillis()
            val userToUpdate = user.copy(updatedAt = currentTime)
            // Validate Shift (Default to "General" if empty)
            val safeShift = user.shift.ifBlank { "General" }
            val safeClassId = user.classId.ifBlank { "NA" }

            // We must use a Map to update the extra key 'class_sync_key'
            val updateMap = mapOf(
                "uid" to userToUpdate.uid,
                "name" to userToUpdate.name,
                "email" to userToUpdate.email,
                "role" to userToUpdate.role,
                "classId" to userToUpdate.classId,
                "className" to userToUpdate.className,
                "divisionId" to userToUpdate.divisionId,
                "divisionName" to userToUpdate.divisionName,
                "phone" to userToUpdate.phone,
                "address" to userToUpdate.address,
                "dateOfBirth" to userToUpdate.dateOfBirth,
                "dateOfJoining" to userToUpdate.dateOfJoining,
                "salary" to userToUpdate.salary,
                "subject" to userToUpdate.subject,
                "parentName" to userToUpdate.parentName,
                "password" to userToUpdate.password,
                "shift" to userToUpdate.shift,
                "totalFees" to userToUpdate.totalFees,
                "classId" to userToUpdate.classId,
                "divisionId" to userToUpdate.divisionId,
                "updatedAt" to userToUpdate.updatedAt,

                // ✅ NEW KEY: ClassID + Shift + Timestamp
                // Example: "-N8s..._Subah_1766500000"
                "class_shift_sync_key" to "${safeClassId}_${safeShift}_$currentTime"
            )

            usersDb.child(user.uid).updateChildren(updateMap).await()
            Result.success(userToUpdate)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error updating user", e)
            Result.failure(e)
        }
    }

    // ✅ TEACHER SYNC (Only My Class Students)
    suspend fun getStudentsForClassUpdatedAfter(
        classId: String,
        shift: String,
        timestamp: Long
    ): Result<List<User>> {
        return try {
            // Key format: ClassID_Shift_Timestamp
            val startKey = "${classId}_${shift}_${timestamp + 1}"
            val endKey = "${classId}_${shift}_9999999999999"

            val snapshot = usersDb
                .orderByChild("class_shift_sync_key")  // ✅ Index Required
                .startAt(startKey)
                .endAt(endKey)
                .get()
                .await()

            val list = snapshot.children.mapNotNull { it.getValue(User::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepo", "Error fetching class students", e)
            Result.failure(e)
        }
    }

    // ✅ GLOBAL SYNC (Admin/General)
    suspend fun getUsersUpdatedAfter(timestamp: Long): Result<List<User>> {
        return try {
            val snapshot = usersDb
                .orderByChild("updatedAt")
                .startAt((timestamp + 1).toDouble())
                .get()
                .await()
            val users = snapshot.children.mapNotNull { it.getValue(User::class.java) }
            Log.d("FirebaseUserRepository", "Fetched ${users.size} users updated after $timestamp")
            Result.success(users)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error getting updated users", e)
            Result.failure(e)
        }
    }

    // ✅ SINGLE USER FETCH (For Student Profile Sync)
    suspend fun getUserById(uid: String): Result<User?> {
        return try {
            val snapshot = usersDb.child(uid).get().await()
            val user = snapshot.getValue(User::class.java)
            Result.success(user)
        } catch (e: Exception) {
            Log.e("FirebaseUserRepository", "Error getting user by id", e)
            Result.failure(e)
        }
    }

    // ... Standard Methods ...

    suspend fun deleteUser(uid: String): Result<Unit> {
        return try {
            usersDb.child(uid).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUsersByRole(role: String): Result<List<User>> {
        return try {
            val snapshot = usersDb.orderByChild("role").equalTo(role).get().await()
            val users = snapshot.children.mapNotNull { it.getValue(User::class.java) }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = usersDb.get().await()
            val users = snapshot.children.mapNotNull { it.getValue(User::class.java) }
            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}