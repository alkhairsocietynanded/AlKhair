package com.aewsn.alkhair.data.manager

import kotlinx.coroutines.launch

import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.data.remote.supabase.SupabaseAuthRepository
import com.aewsn.alkhair.data.remote.supabase.SupabaseUserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepoManager @Inject constructor(
    private val authRepository: SupabaseAuthRepository,
    private val userRepository: SupabaseUserRepository, // Inject Remote Repo for fresh installs
    private val userRepoManager: UserRepoManager, // Inject Manager for Local Sync
    private val appDataStore: com.aewsn.alkhair.data.datastore.AppDataStore
) {

    companion object {
        private const val KEY_CURRENT_UID = "current_user_uid"
    }

    /**
     * Login Logic:
     * 1. Auth against Supabase Auth.
     * 2. Check Local DB for User Profile.
     * 3. If Local is empty (Fresh Install), fetch from Supabase.
     * 4. Save to Local DB.
     */
    suspend fun login(email: String, password: String): Result<User> {
        return authRepository.login(email, password).fold(
            onSuccess = { uid ->
                try {
                    // 1. Try Local First (Fastest)
                    saveLoginState(uid) // ✅ Persist Login State
                    val localUser = userRepoManager.getUserById(uid)
                    if (localUser != null) {
                        return Result.success(localUser)
                    }

                    // 2. If Local failed (New Device/Fresh Install), Fetch Remote
                    val remoteUserResult = userRepository.getUserById(uid)

                    remoteUserResult.fold(
                        onSuccess = { remoteUser ->
                            if (remoteUser != null) {
                                // 3. Save to Local DB (Sync)
                                // ERROR FIXED: insertLocal is protected. Use saveUserLocally (see UserRepoManager note below)
                                userRepoManager.saveUserLocally(remoteUser)
                                saveLoginState(uid) // ✅ Persist Login State
                                Result.success(remoteUser)
                            } else {
                                Result.failure(Exception("User profile not found in database."))
                            }
                        },
                        onFailure = { e ->
                            Result.failure(e)
                        }
                    )
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = { e ->
                Result.failure(e)
            }
        )
    }

    /**
     * Signup Logic:
     * 1. Create Auth Account.
     * 2. Use UserRepoManager to Create User (Remote -> Local).
     */
    suspend fun signup(user: User): Result<User> {
        return authRepository.signup(user).fold(
            onSuccess = { uid ->
                // 1. Encrypt Password before saving locally

                val finalUser = user.copy(
                    uid = uid,
                    isSynced = true, // ✅ Synced via Edge Function
                    updatedAt = System.currentTimeMillis()
                )
                
                // 2. Save Local Only (No Worker needed as Edge Function synced it)
                userRepoManager.saveUserLocally(finalUser)
                saveLoginState(uid) // ✅ Persist Login State
                Result.success(finalUser)
            },
            onFailure = { e ->
                Result.failure(e)
            }
        )
    }

    suspend fun logout() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            clearLoginState() // ✅ Clear Persistent State
            authRepository.logout()
        }
    }

    /* ============================================================
       🔐 Session Persistence Utilities
       ============================================================ */

    suspend fun saveLoginState(uid: String) {
        appDataStore.saveString(KEY_CURRENT_UID, uid)
    }

    private suspend fun clearLoginState() {
        // Clear EVERYTHING (Session, Sync Timestamps, etc) on logout to prevent data leakage/sync issues
        appDataStore.clearAll()
    }

    suspend fun getLocalLoginUid(): String? {
        val uid = appDataStore.getString(KEY_CURRENT_UID, "")
        return if (uid.isNotEmpty()) uid else null
    }

    fun getCurrentUserUid(): String? {
        return authRepository.currentUserUid()
    }

    fun monitorSession() = authRepository.getSessionStatus()
}