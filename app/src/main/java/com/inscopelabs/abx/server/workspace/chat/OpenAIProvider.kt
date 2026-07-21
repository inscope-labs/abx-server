package com.inscopelabs.abx.server.workspace.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OpenAIProvider(apiKey: String) : BaseChatProvider(apiKey) {

    override val providerName: String = "openai"

    private val baseUrl = "https://api.openai.com/v1/chat/completions"

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

    override fun capabilities(): List<String> = listOf(
        "tool_calling", "json_mode", "vision", "function_calling"
    )
}