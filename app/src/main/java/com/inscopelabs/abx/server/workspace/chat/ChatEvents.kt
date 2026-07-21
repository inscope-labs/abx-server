package com.inscopelabs.abx.server.workspace.chat

sealed class ChatEvent {
    data class SessionCreated(val session: ChatSession) : ChatEvent()
    data class SessionDeleted(val sessionId: String) : ChatEvent()
    data class SessionUpdated(val session: ChatSession) : ChatEvent()
    data class MessageAdded(val sessionId: String, val message: Message) : ChatEvent()
    data class StreamingChunk(val sessionId: String, val chunk: String) : ChatEvent()
    data class StateChanged(val sessionId: String, val state: StreamingState) : ChatEvent()
    data class ErrorOccurred(val sessionId: String, val throwable: Throwable) : ChatEvent()
    data class ConversationCleared(val sessionId: String) : ChatEvent()
}