package com.mistakenotes.data.rag

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * DeepSeek Chat Completions API
 *
 * 文档：https://api-docs.deepseek.com/
 */
interface DeepSeekApi {

    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse
}

@Serializable
data class ChatRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,  // 低温度让分类更稳定
    @SerialName("response_format") val responseFormat: ResponseFormat? = null
)

@Serializable
data class ChatMessage(
    val role: String,  // "system" / "user" / "assistant"
    val content: String
)

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>
)

@Serializable
data class ChatChoice(
    val message: ChatMessage
)

/**
 * RAG 精排 prompt 让 LLM 输出的 JSON 结构
 */
@Serializable
data class RerankResult(
    val chapterId: Long,
    val knowledgePointId: Long,
    val confidence: Float,
    val reasoning: String = ""
)
