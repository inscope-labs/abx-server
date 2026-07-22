package com.inscopelabs.abx.server.workspace.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Handles OpenAI's chat/completions schema. Also used for Groq, DeepSeek,
 * Mistral, and OpenRouter via ProviderFactory, since they all speak the
 * OpenAI-compatible completions format; only the base URL differs.
 */
class OpenAIProvider(
    apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1/chat/completions"
) : BaseChatProvider(apiKey) {

    val providerName: String = "openai"

    override fun buildRequest(prompt: String, settings: ChatSettings): Request {
        val json = JSONObject().apply {
            put("model", settings.model)
            put("messages", listOf(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
            put("temperature", settings.temperature)
            put("max_tokens", settings.maxTokens)
            put("stream", true)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url(baseUrl)
            .post(body)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
    }

    /**
     * Each SSE event's `data` field is either the literal string "[DONE]",
     * or a JSON chunk like:
     *   {"choices":[{"delta":{"content":"..."}}]}
     * Extract just the incremental token text.
     */
    override fun parseChunk(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return ""
        return try {
            val obj = JSONObject(trimmed)
            val choices = obj.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return ""
            delta.optString("content", "")
        } catch (e: Exception) {
            ""
        }
    }

    override fun capabilities(): List<String> = listOf(
        "tool_calling", "json_mode", "vision", "function_calling"
    )
}
