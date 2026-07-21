package com.inscopelabs.abx.server.workspace.chat

class StreamingParser {
    fun parseChunk(rawSseLine: String): String {
        if (rawSseLine.startsWith("data: ")) {
            val data = rawSseLine.removePrefix("data: ").trim()
            if (data == "[DONE]") return ""
            // For OpenAI, data is JSON; extract "content" from delta
            // For simplicity, return the raw data; subclass providers can override.
            return data
        }
        return rawSseLine
    }
}