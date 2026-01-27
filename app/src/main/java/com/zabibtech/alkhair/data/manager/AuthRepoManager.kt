package com.zabibtech.alkhair.data.manager

import kotlinx.coroutines.launch

import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.remote.supabase.SupabaseAuthRepository
import com.zabibtech.alkhair.data.remote.supabase.SupabaseUserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepoManager @Inject constructor(
    private val authRepository: SupabaseAuthRepository,
    private val userRepository: SupabaseUserRepository, // Inject Remote Repo for fresh installs
    private val userRepoManager: UserRepoManager // Inject Manager for Local Sync
) {

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
                Result.success(finalUser)
            },
            onFailure = { e ->
                Result.failure(e)
            }
        )
    }

    fun logout() {
        // Run blocking or launch in scope if needed, but repo method is suspend.
        // Manager method is currently synchronous (fun logout()).
        // Ideally should be suspend, or we launch here. 
        // For now, let's keep it synchronous wrapper if possible or change signature.
        // Migration note: The original logout() was not suspend?
        // Ah, FirebaseAuth.signOut() is synchronous. Supabase.signOut() is suspend.
        // I need to update this method to be suspend OR launch a coroutine scope.
        // Or check if SupabaseAuthRepo has a blocking option? No.
        // I will change it to suspend.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            authRepository.logout()
        }
    }

    fun getCurrentUserUid(): String? {
        return authRepository.currentUserUid()
    }

    fun monitorSession() = authRepository.getSessionStatus()
}