package com.inscopelabs.abx.server.workspace.chat

class ChatHistory(private val repository: ChatRepository) {
    suspend fun fetchHistory(sessionId: String): List<Message> {
        return repository.getSession(sessionId)?.messages ?: emptyList()
    }

    suspend fun clearHistory(sessionId: String) {
        val session = repository.getSession(sessionId) ?: return
        val updated = session.copy(messages = emptyList(), updatedAt = System.currentTimeMillis())
        repository.saveSession(updated)
    }

    suspend fun trimHistory(sessionId: String, maxMessages: Int) {
        val session = repository.getSession(sessionId) ?: return
        if (session.messages.size > maxMessages) {
            val trimmed = session.messages.takeLast(maxMessages)
            val updated = session.copy(messages = trimmed, updatedAt = System.currentTimeMillis())
            repository.saveSession(updated)
        }
    }
}