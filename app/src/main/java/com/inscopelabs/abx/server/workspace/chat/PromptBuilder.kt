package com.inscopelabs.abx.server.workspace.chat

class PromptBuilder(private val tokenCounter: TokenCounter) {

    fun build(
        systemPrompt: String,
        history: List<Message>,
        workspaceContext: String,
        userPrompt: String,
        attachments: List<Attachment> = emptyList(),
        maxTokens: Int,
        memorySummary: String = ""
    ): String {
        val sb = StringBuilder()

        // System instruction
        if (systemPrompt.isNotEmpty()) {
            sb.append("System: $systemPrompt\n\n")
        }

        // Memory summary (if any)
        if (memorySummary.isNotEmpty()) {
            sb.append("Memory Summary: $memorySummary\n\n")
        }

        // Workspace context
        if (workspaceContext.isNotEmpty()) {
            sb.append("Workspace Context:\n$workspaceContext\n\n")
        }

        // Attachments info (if any)
        if (attachments.isNotEmpty()) {
            sb.append("Attachments:\n")
            attachments.forEach { attachment ->
                sb.append("- ${attachment.name} (${attachment.type}, ${attachment.sizeBytes} bytes)\n")
            }
            sb.append("\n")
        }

        // Conversation history (excluding the last user message if it's already in the prompt)
        // We'll include all history except the very last user message if it matches the userPrompt
        val filteredHistory = history.toMutableList().apply {
            // Remove the last message if it's a USER message and its content equals userPrompt
            if (isNotEmpty() && last().role == MessageRole.USER && last().content == userPrompt) {
                removeAt(lastIndex)
            }
        }

        for (msg in filteredHistory) {
            val role = when (msg.role) {
                MessageRole.SYSTEM -> "System"
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.TOOL -> "Tool"
            }
            sb.append("$role: ${msg.content}\n")
        }

        // Final user prompt
        sb.append("User: $userPrompt\n")

        val assembled = sb.toString()

        // Token budget enforcement: trim from the beginning if over limit
        if (tokenCounter.countTokens(assembled) > maxTokens) {
            // Simple truncation: keep last ~80% of characters (approximate)
            val targetLength = maxTokens * 4 // ~4 chars per token
            val trimmed = if (assembled.length > targetLength) {
                val start = assembled.length - targetLength
                "... (truncated)\n" + assembled.substring(start)
            } else {
                assembled
            }
            return trimmed
        }

        return assembled
    }
}