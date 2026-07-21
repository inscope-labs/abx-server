package com.inscopelabs.abx.server.workspace.chat

import kotlinx.coroutines.flow.Flow

interface ChatProvider {
    suspend fun sendMessage(prompt: String, settings: ChatSettings): Flow<String>
    fun supportsCapability(capability: String): Boolean
}
