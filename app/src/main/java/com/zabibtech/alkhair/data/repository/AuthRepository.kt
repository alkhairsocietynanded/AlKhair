package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.utils.FirebaseRefs.auth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor() {

    suspend fun signup(email: String, password: String): String {
        val res = auth.createUserWithEmailAndPassword(email, password).await()
        return res.user?.uid ?: throw Exception("Signup failed: UID null")
    }

    suspend fun login(email: String, password: String): String {
        val res = auth.signInWithEmailAndPassword(email, password).await()
        return res.user?.uid ?: throw Exception("Login failed: UID null")
    }

    fun currentUserUid(): String? = auth.currentUser?.uid

    fun logout() = auth.signOut()
}
