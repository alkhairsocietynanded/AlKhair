package com.aewsn.alkhair.data.remote.supabase

import android.util.Log
import com.aewsn.alkhair.data.models.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    suspend fun signup(user: User): Result<String> {
        return try {
            // ✅ Fix: Use buildJsonObject to avoid "Serializing collections of different element types" error
            val jsonBody = buildJsonObject {
                put("email", user.email)
                put("password", user.password)
                put("name", user.name)
                put("role", user.role)
                put("phone", user.phone)
                put("address", user.address)
                put("subject", user.subject)
                put("shift", user.shift)
                put("parent_name", user.parentName)
                put("date_of_birth", user.dateOfBirth)
                put("date_of_joining", user.dateOfJoining)
                put("total_fees", user.totalFees)
                put("salary", user.salary)

                // Handle Nullables explicitly
                if (user.classId != null) put("class_id", user.classId)
                if (user.divisionId != null) put("division_id", user.divisionId)
            }

            val response = supabase.functions.invoke("create-user") {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }

            // Response is strictly HttpResponse in this version
            val responseText = response.bodyAsText()
            val data = Json.parseToJsonElement(responseText).jsonObject

            // Access properties safely
            val uid = data["id"]?.jsonPrimitive?.content
            // Note: id in public.users is same as auth.users id
                ?: data["user"]?.jsonObject?.get("id")?.jsonPrimitive?.content

            if (uid != null) {
                Result.success(uid)
            } else {
                Result.failure(Exception("Signup successful but no UID returned"))
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuthRepo", "Error signing up via Edge Function", e)
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val uid = supabase.auth.currentUserOrNull()?.id
                ?: throw Exception("Login failed: UID is null")
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

    fun getSessionStatus(): Flow<SessionStatus> {
        return supabase.auth.sessionStatus
    }

    suspend fun logout() {
        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            Log.e("SupabaseAuthRepo", "Error logging out", e)
        }
    }
}
