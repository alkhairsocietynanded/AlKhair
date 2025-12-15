package com.zabibtech.alkhair.data.manager

import android.util.Log
import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.data.remote.firebase.FirebaseAuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepoManager @Inject constructor(
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val userRepoManager: UserRepoManager
) {

    suspend fun login(email: String, password: String): Result<User> {
        // First, login with Firebase Auth
        return firebaseAuthRepository.login(email, password).fold(
            onSuccess = { uid ->
                // If login is successful, fetch the user details from the database
                userRepoManager.getUserById(uid).fold(
                    onSuccess = { user ->
                        Log.d("Auth:Login", "login: $user || uid = $uid")
                        if (user != null) {
                            Result.success(user)
                        } else {
                            Result.failure(Exception("User record not found in the database."))
                        }
                    },
                    onFailure = { e ->
                        Result.failure(e)
                    }
                )
            },
            onFailure = { e ->
                // If login fails, return the failure
                Result.failure(e)
            }
        )
    }

    suspend fun signup(email: String, password: String, user: User): Result<User> {
        // First, create a user with Firebase Auth
        return firebaseAuthRepository.signup(email, password).fold(
            onSuccess = { uid ->
                // If signup is successful, save the user details to the database
                val finalUser = user.copy(uid = uid)
                userRepoManager.createUser(finalUser)
                
            },
            onFailure = { e ->
                // If signup fails, return the failure
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
