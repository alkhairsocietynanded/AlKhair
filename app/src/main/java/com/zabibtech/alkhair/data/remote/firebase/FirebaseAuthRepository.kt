package com.zabibtech.alkhair.data.remote.firebase

import android.util.Log
import com.zabibtech.alkhair.utils.FirebaseRefs.auth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor() {

    suspend fun signup(email: String, password: String): Result<String> {
        return try {
            val res = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = res.user?.uid ?: throw Exception("Signup failed: UID is null")
            Result.success(uid)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Error signing up", e)
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val res = auth.signInWithEmailAndPassword(email, password).await()
            val uid = res.user?.uid ?: throw Exception("Login failed: UID is null")
            Result.success(uid)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Error logging in", e)
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepo", "Error sending password reset email", e)
            Result.failure(e)
        }
    }

    fun currentUserUid(): String? = auth.currentUser?.uid

    fun logout() = auth.signOut()
    
}