package com.aewsn.alkhair.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

import retrofit2.http.Url

interface n8nApiService {
    @POST
    suspend fun sendMessage(@Url url: String, @Body request: AiChatRequest): Response<AiChatResponse>
}
data class AiChatRequest(
    val chatInput: String,
    val sessionId: String
)

data class AiChatResponse(
    val output: String? = null,
    val text: String? = null // Handle variations
)
