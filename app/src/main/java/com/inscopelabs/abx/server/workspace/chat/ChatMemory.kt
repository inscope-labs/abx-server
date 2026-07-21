package com.inscopelabs.abx.server.workspace.chat

class ChatMemory(private val tokenCounter: TokenCounter) {

    /**
     * Enforce token budget by trimming oldest messages.
     */
    fun enforceBudget(messages: List<Message>, maxTokens: Int): List<Message> {
        if (messages.isEmpty()) return messages
        var totalTokens = tokenCounter.countMessageTokens(messages)
        val mutableList = messages.toMutableList()
        while (totalTokens > maxTokens && mutableList.size > 1) {
            val removed = mutableList.removeAt(0)
            totalTokens -= tokenCounter.countTokens(removed.content) + 4 // rough overhead
        }
        return mutableList
    }

    /**
     * Generate a summary of the conversation (placeholder; could use AI summarization).
     */
    fun summarizeContext(messages: List<Message>): String {
        if (messages.isEmpty()) return ""
        val userMessages = messages.filter { it.role == MessageRole.USER }
        val assistantMessages = messages.filter { it.role == MessageRole.ASSISTANT }
        return "Previous conversation: ${userMessages.size} user messages, ${assistantMessages.size} assistant messages."
    }
}