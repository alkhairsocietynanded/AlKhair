package com.aewsn.alkhair.data.repository

import com.aewsn.alkhair.data.remote.ChatRequest
import com.aewsn.alkhair.data.remote.n8nApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: n8nApiService,
    private val appConfigRepoManager: com.aewsn.alkhair.data.manager.AppConfigRepoManager
) {
    // Default URL if DB is empty (Same as before)
    private val DEFAULT_N8N_URL = "https://revlum4e5b.app.n8n.cloud/webhook/bc32af76-83ae-4b76-aef1-4f87dd376dd1"

    fun sendMessage(message: String, sessionId: String): Flow<Result<String>> = flow {
        try {
            // 1. Fetch dynamic URL
            val dynamicUrl = appConfigRepoManager.getConfigValue("n8n_url") ?: DEFAULT_N8N_URL
            
            // 2. Call API with dynamic URL
            val response = apiService.sendMessage(dynamicUrl, ChatRequest(chatInput = message, sessionId = sessionId))
            
            if (response.isSuccessful && response.body() != null) {
                // n8n now returns a single object { "output": "...", "text": "..." }
                val body = response.body()!!
                
                // Try to get 'output' or 'text' or fallback
                var reply = body.output ?: body.text ?: "No response text found"

                // Clean up the response (Safer check for \n\n---  \n\n)
                // The agent appends "\n\n--- \n\n*Query ko cache..."
                if (reply.contains("\n\n---")) {
                    reply = reply.split("\n\n---")[0].trim()
                }

                emit(Result.success(reply))
            } else {
                emit(Result.failure(Exception("Error: ${response.code()} ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
}
