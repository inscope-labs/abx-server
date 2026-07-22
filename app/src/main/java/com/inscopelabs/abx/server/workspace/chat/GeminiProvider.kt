package com.inscopelabs.abx.server.workspace.chat

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

class GeminiProvider(apiKey: String) : BaseChatProvider(apiKey) {

    val providerName: String = "gemini"

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    override fun buildRequest(prompt: String, settings: ChatSettings): Request {
        val model = settings.model
        // Must be streamGenerateContent with alt=sse to get an SSE stream at
        // all — the plain :generateContent endpoint returns one JSON object
        // and never emits SSE "data:" events, so BaseChatProvider's
        // EventSource-based consumer would just hang waiting for events
        // that never arrive.
        val url = "$baseUrl/$model:streamGenerateContent?alt=sse&key=$apiKey"

        val json = JSONObject().apply {
            put("contents", listOf(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", listOf(
                        JSONObject().apply {
                            put("text", prompt)
                        }
                    ))
                }
            ))
            // Additional generation config
            put("generationConfig", JSONObject().apply {
                put("temperature", settings.temperature)
                put("maxOutputTokens", settings.maxTokens)
                put("topP", settings.topP)
                put("topK", settings.topK)
            })
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
    }

    /**
     * Each Gemini SSE event's `data` field is a full GenerateContentResponse
     * JSON object, e.g.
     *   {"candidates":[{"content":{"parts":[{"text":"..."}],"role":"model"}}]}
     * Extract just the incremental text. Responses that were blocked by
     * safety filters, or a stray keep-alive with no candidates, yield "".
     */
    override fun parseChunk(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "[DONE]") return ""
        return try {
            val obj = JSONObject(trimmed)
            val candidates = obj.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    override fun capabilities(): List<String> = listOf(
        "vision", "tool_calling", "json_mode", "reasoning"
    )
}
