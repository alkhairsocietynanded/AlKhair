package com.zabibtech.alkhair.data.remote.supabase

import com.zabibtech.alkhair.data.datastore.AppDataStore
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AndroidSessionManager @Inject constructor(
    private val appDataStore: AppDataStore
) : SessionManager {

    // Shared Json instance with lenient configuration
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    override suspend fun saveSession(session: UserSession) {
        val sessionString = json.encodeToString(session)
        appDataStore.saveString("supabase_session", sessionString)
    }

    override suspend fun loadSession(): UserSession? {
        val sessionString = appDataStore.getString("supabase_session", "")
        if (sessionString.isEmpty()) return null
        
        return try {
            json.decodeFromString<UserSession>(sessionString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun deleteSession() {
        appDataStore.clearKey("supabase_session")
    }
}
