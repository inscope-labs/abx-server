package com.inscopelabs.abx.server.workspace.chat

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

class GeminiProvider(apiKey: String) : BaseChatProvider(apiKey) {

    override val providerName: String = "gemini"

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    override fun buildRequest(prompt: String, settings: ChatSettings): Request {
        val model = settings.model
        val url = "$baseUrl/$model:generateContent?key=$apiKey"

        val json = JSONObject().apply {
            put("contents", listOf(
                JSONObject().apply {
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

    override fun capabilities(): List<String> = listOf(
        "vision", "tool_calling", "json_mode", "reasoning"
    )
}