package com.inscopelabs.abx.server.workspace.chat

import java.util.UUID

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }
enum class MessageStatus { SENDING, SENT, STREAMING, COMPLETE, ERROR }
enum class StreamingState { IDLE, LOADING, CONNECTING, RECEIVING, THINKING, RETRYING, CANCELLED, DONE, ERROR }

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val uri: String,
    val name: String,
    val sizeBytes: Long = 0
) {
    companion object
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val tokenCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
) {
    companion object
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val provider: String,
    val model: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val settings: ChatSettings = ChatSettings(),
    val messages: List<Message> = emptyList()
)

data class ChatSettings(
    val provider: String = "gemini",
    val model: String = "gemini-1.5-pro",
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxTokens: Int = 4096,
    val timeoutMillis: Long = 30000L,
    val stream: Boolean = true,
    val retryCount: Int = 3,
    val memoryLimit: Int = 10
) {
    // Simple JSON serialization (for Room)
    fun toJson(): String = """
        {
            "provider":"$provider",
            "model":"$model",
            "temperature":$temperature,
            "topP":$topP,
            "topK":$topK,
            "maxTokens":$maxTokens,
            "timeoutMillis":$timeoutMillis,
            "stream":$stream,
            "retryCount":$retryCount,
            "memoryLimit":$memoryLimit
        }
    """.trimIndent()

    companion object {
        fun fromJson(json: String): ChatSettings {
            // For simplicity, assume default; in production use Gson or Moshi
            return ChatSettings()
        }
    }
}

// Extension functions for JSON (simplified, use a real JSON library in production)
@JvmName("messagesToJson")
fun List<Message>.toJson(): String = "" // implement with Gson
fun Message.Companion.fromJsonArray(json: String): List<Message> = emptyList()
@JvmName("attachmentsToJson")
fun List<Attachment>.toJson(): String = ""
fun Attachment.Companion.fromJsonArray(json: String): List<Attachment> = emptyList()