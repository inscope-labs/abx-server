package com.inscopelabs.abx.server.workspace.chat

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
    // gemini-1.5-pro was fully retired; gemini-2.5-flash is the current
    // supported low-latency default. Keep in sync with ChatManager's
    // createSession() default and ProviderFactory's supported provider list.
    val model: String = "gemini-2.5-flash",
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxTokens: Int = 4096,
    val timeoutMillis: Long = 30000L,
    val stream: Boolean = true,
    val retryCount: Int = 3,
    val memoryLimit: Int = 10
) {
    fun toJson(): String = ChatJson.settingsAdapter.toJson(this)

    companion object {
        fun fromJson(json: String): ChatSettings {
            if (json.isBlank()) return ChatSettings()
            return try {
                ChatJson.settingsAdapter.fromJson(json) ?: ChatSettings()
            } catch (e: Exception) {
                // Corrupt/legacy row (e.g. from the old stub serializer) — fall
                // back to defaults rather than crashing session load.
                ChatSettings()
            }
        }
    }
}

/**
 * Shared Moshi instance + adapters used to persist chat domain objects as
 * JSON strings via Room TypeConverters (see Converters in ChatRepository.kt).
 *
 * Replaces the previous stub implementation, which always wrote empty
 * strings and always read back empty/default values — meaning every
 * session reload silently discarded all messages and settings.
 */
internal object ChatJson {
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val settingsAdapter = moshi.adapter(ChatSettings::class.java)

    private val messageListType = Types.newParameterizedType(List::class.java, Message::class.java)
    val messageListAdapter = moshi.adapter<List<Message>>(messageListType)

    private val attachmentListType = Types.newParameterizedType(List::class.java, Attachment::class.java)
    val attachmentListAdapter = moshi.adapter<List<Attachment>>(attachmentListType)
}

@JvmName("messagesToJson")
fun List<Message>.toJson(): String = ChatJson.messageListAdapter.toJson(this)

fun Message.Companion.fromJsonArray(json: String): List<Message> {
    if (json.isBlank()) return emptyList()
    return try {
        ChatJson.messageListAdapter.fromJson(json) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

@JvmName("attachmentsToJson")
fun List<Attachment>.toJson(): String = ChatJson.attachmentListAdapter.toJson(this)

fun Attachment.Companion.fromJsonArray(json: String): List<Attachment> {
    if (json.isBlank()) return emptyList()
    return try {
        ChatJson.attachmentListAdapter.fromJson(json) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
