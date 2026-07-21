package com.inscopelabs.abx.server.workspace.chat

class ProviderFactory {
    fun createProvider(providerName: String, apiKey: String): ChatProvider {
        return when (providerName.lowercase()) {
            "openai", "groq", "deepseek", "mistral", "openrouter" -> OpenAIProvider(apiKey)
            "gemini" -> GeminiProvider(apiKey)
            else -> throw IllegalArgumentException("Unsupported provider: $providerName")
        }
    }
}