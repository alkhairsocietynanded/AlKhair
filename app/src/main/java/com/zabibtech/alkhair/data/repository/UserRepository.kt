package com.zabibtech.alkhair.data.repository

import com.zabibtech.alkhair.data.models.User
import com.zabibtech.alkhair.utils.FirebaseRefs.usersDb
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepository @Inject constructor() {

    suspend fun createUser(user: User): User {
        usersDb.child(user.uid).setValue(user).await()
        return user
    }

    suspend fun updateUser(user: User): User {
        usersDb.child(user.uid).setValue(user).await()
        return user
    }

    suspend fun getUserById(uid: String): User? {
        return usersDb.child(uid).get().await().getValue(User::class.java)
    }

    suspend fun deleteUser(uid: String) {
        usersDb.child(uid).removeValue().await()
    }

    suspend fun getUsers(role: String): List<User> {
        val snapshot = usersDb.orderByChild("role").equalTo(role).get().await()
        return snapshot.children
            .mapNotNull { it.getValue(User::class.java) }
            .sortedBy { it.name.lowercase() }
    }

}