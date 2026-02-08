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
    private val apiService: n8nApiService
) {
    fun sendMessage(message: String, sessionId: String): Flow<Result<String>> = flow {
        try {
            val response = apiService.sendMessage(ChatRequest(chatInput = message, sessionId = sessionId))
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
