package com.aewsn.alkhair.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface n8nApiService {
    @POST("webhook/bc32af76-83ae-4b76-aef1-4f87dd376dd1")
    suspend fun sendMessage(@Body request: ChatRequest): Response<ChatResponse>
}
data class ChatRequest(
    val chatInput: String,
    val sessionId: String
)

data class ChatResponse(
    val output: String? = null,
    val text: String? = null // Handle variations
)
