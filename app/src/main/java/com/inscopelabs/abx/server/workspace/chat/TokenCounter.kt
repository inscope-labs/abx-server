package com.inscopelabs.abx.server.workspace.chat

class TokenCounter {
    // Approximate token counting (use a real tokenizer in production, e.g., tiktoken)
    fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        // Rough estimate: ~4 chars per token for English
        return (text.length / 4).coerceAtLeast(1)
    }

    fun countMessageTokens(messages: List<Message>): Int {
        return messages.sumOf { countTokens(it.content) + 4 } // +4 for role/meta tokens
    }
}