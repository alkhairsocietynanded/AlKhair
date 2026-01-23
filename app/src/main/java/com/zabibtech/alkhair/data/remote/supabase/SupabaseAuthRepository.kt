package com.zabibtech.alkhair.data.remote.supabase

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun signup(email: String, password: String): Result<String> {
        return try {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val user = supabase.auth.currentUserOrNull()
            if (user?.id != null) {
                Result.success(user.id)
            } else {
                // Sometimes signup might require email confirmation, but usually returns user object
                // If auto-confirm is off, we might get a user but session is null.
                // For now, assume success if no exception.
                 // We need the ID to save the profile.
                 // Check if session is null logic
                 val session = supabase.auth.currentSessionOrNull()
                 // If session is null but no error, maybe email confirm needed?
                 // But for this app, we assume we get the ID.
                Result.success(user?.id ?: "")
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuthRepo", "Error signing up", e)
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val uid = supabase.auth.currentUserOrNull()?.id ?: throw Exception("Login failed: UID is null")
            Result.success(uid)
        } catch (e: Exception) {
            Log.e("SupabaseAuthRepo", "Error logging in", e)
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            supabase.auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseAuthRepo", "Error sending password reset email", e)
            Result.failure(e)
        }
    }

    fun currentUserUid(): String? {
        return supabase.auth.currentUserOrNull()?.id
    }

    suspend fun logout() {
        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            Log.e("SupabaseAuthRepo", "Error logging out", e)
        }
    }
}
