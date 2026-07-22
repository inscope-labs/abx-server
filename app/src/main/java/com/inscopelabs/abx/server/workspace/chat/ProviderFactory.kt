package com.inscopelabs.abx.server.workspace.chat

class ProviderFactory {
    fun createProvider(providerName: String, apiKey: String): ChatProvider {
        return when (providerName.lowercase()) {
            // These all speak the OpenAI-compatible chat/completions schema —
            // only the base URL differs. Previously all four fell through to
            // OpenAIProvider(apiKey) with no base URL override, which meant
            // selecting e.g. Groq silently sent the request (with a Groq key)
            // to api.openai.com instead of Groq's endpoint.
            "openai" -> OpenAIProvider(apiKey)
            "groq" -> OpenAIProvider(apiKey, baseUrl = "https://api.groq.com/openai/v1/chat/completions")
            "deepseek" -> OpenAIProvider(apiKey, baseUrl = "https://api.deepseek.com/chat/completions")
            "mistral" -> OpenAIProvider(apiKey, baseUrl = "https://api.mistral.ai/v1/chat/completions")
            "openrouter" -> OpenAIProvider(apiKey, baseUrl = "https://openrouter.ai/api/v1/chat/completions")
            "gemini" -> GeminiProvider(apiKey)
            else -> throw IllegalArgumentException("Unsupported provider: $providerName")
        }
    }

    companion object {
        /** Providers selectable from the chat settings UI. */
        val SUPPORTED_PROVIDERS = listOf("gemini", "openai", "groq", "deepseek", "mistral", "openrouter")
    }
}
