package com.inscopelabs.abx.server.workspace.chat

import android.util.Log

class ChatLogger {
    companion object {
        private const val TAG = "ABXChatSubsystem"
    }

    fun logRequest(provider: String, model: String, promptLength: Int) {
        Log.d(TAG, "Request -> Provider: $provider, Model: $model, Prompt Length: $promptLength")
    }

    fun logResponse(provider: String, latencyMs: Long, tokenCount: Int) {
        Log.d(TAG, "Response <- Provider: $provider, Latency: ${latencyMs}ms, Tokens: $tokenCount")
    }

    fun logProviderError(provider: String, throwable: Throwable) {
        Log.e(TAG, "Error in Provider: $provider", throwable)
    }

    fun logError(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }

    fun logCancellation(sessionId: String) {
        Log.w(TAG, "Session execution cancelled: $sessionId")
    }
}