package com.zabibtech.alkhair.data.manager

import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAuthRepository
import com.zabibtech.alkhair.data.remote.firebase.FirebaseUserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepoManager @Inject constructor(
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val firebaseUserRepository: FirebaseUserRepository, // Inject Remote Repo for fresh installs
    private val userRepoManager: UserRepoManager // Inject Manager for Local Sync
) {

    /**
     * Login Logic:
     * 1. Auth against Firebase Auth.
     * 2. Check Local DB for User Profile.
     * 3. If Local is empty (Fresh Install), fetch from Firebase Firestore.
     * 4. Save to Local DB.
     */
    suspend fun login(email: String, password: String): Result<User> {
        return firebaseAuthRepository.login(email, password).fold(
            onSuccess = { uid ->
                try {
                    // 1. Try Local First (Fastest)
                    val localUser = userRepoManager.getUserById(uid)
                    if (localUser != null) {
                        return Result.success(localUser)
                    }

                    // 2. If Local failed (New Device/Fresh Install), Fetch Remote
                    val remoteUserResult = firebaseUserRepository.getUserById(uid)

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
    suspend fun signup(email: String, password: String, user: User): Result<User> {
        return firebaseAuthRepository.signup(email, password).fold(
            onSuccess = { uid ->
                // 1. Encrypt Password before saving locally
                val encryptedPassword = com.zabibtech.alkhair.utils.EncryptionUtils.encrypt(password)
                
                val finalUser = user.copy(
                    uid = uid,
                    password = encryptedPassword,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
                
                // 2. Save Local & Schedule Sync (Hybrid Flow)
                // We use createUser from UserRepoManager which handles Local Insert + Worker Schedule
                userRepoManager.createUser(finalUser)
            },
            onFailure = { e ->
                Result.failure(e)
            }
        )
    }

    fun logout() {
        firebaseAuthRepository.logout()
    }

    fun getCurrentUserUid(): String? {
        return firebaseAuthRepository.currentUserUid()
    }
}