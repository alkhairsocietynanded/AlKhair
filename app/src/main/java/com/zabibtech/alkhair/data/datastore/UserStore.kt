package com.zabibtech.alkhair.data.datastore

import com.zabibtech.alkhair.data.models.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserStore @Inject constructor(
    private val appDataStore: AppDataStore
) {
    companion object {
        private const val USER_KEY = "user_data"
    }

    // ==============================
    // User session management
    // ==============================
    suspend fun saveUser(user: User) {
        appDataStore.saveObject(USER_KEY, user)
    }

    suspend fun getUser(): User? {
        return appDataStore.getObject(USER_KEY)
    }

    suspend fun clearUser() {
        appDataStore.clearKey(USER_KEY)
    }
}