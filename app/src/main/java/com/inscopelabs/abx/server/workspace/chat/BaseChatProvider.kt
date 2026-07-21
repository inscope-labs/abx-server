package com.inscopelabs.abx.server.workspace.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Abstract base for AI provider implementations.
 * Handles SSE streaming, retries, authentication, and common request logic.
 */
abstract class BaseChatProvider(
    protected val apiKey: String,
    protected val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : ChatProvider {

    protected val streamingParser = StreamingParser()

    /**
     * Default implementation of sendMessage that uses SSE streaming.
     * Subclasses override [buildRequest] and [parseChunk] if needed.
     */
    override suspend fun sendMessage(prompt: String, settings: ChatSettings): Flow<String> = flow {
        val request = buildRequest(prompt, settings)
        val eventSourceFactory = EventSources.createFactory(httpClient)

        // Use a channel to bridge SSE events to flow
        val channel = kotlinx.coroutines.channels.Channel<String>(capacity = 64)

        eventSourceFactory.newEventSource(request, object : okhttp3.sse.EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                // connection opened
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val chunk = streamingParser.parseChunk(data)
                if (chunk.isNotEmpty()) {
                    // Use trySend to avoid blocking
                    channel.trySend(chunk)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                channel.close(t ?: IOException("SSE connection failed"))
            }
        })

        // Consume from channel until closed
        try {
            for (chunk in channel) {
                emit(chunk)
            }
        } catch (e: Exception) {
            // handle error
            throw e
        } finally {
            channel.close()
        }
    }

    /**
     * Build the HTTP request for the specific provider.
     * Must include headers (Authorization, Content-Type) and body.
     */
    protected abstract fun buildRequest(prompt: String, settings: ChatSettings): Request

    /**
     * Override to implement custom chunk parsing (e.g., JSON extraction).
     * Default uses StreamingParser.
     */
    protected open fun parseChunk(raw: String): String {
        return streamingParser.parseChunk(raw)
    }

    /**
     * Execute a block with retries (exponential backoff).
     */
    protected suspend fun <T> executeWithRetry(
        retries: Int,
        initialDelayMs: Long = 500,
        block: suspend () -> T
    ): T {
        var currentAttempt = 0
        var delayMs = initialDelayMs
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (currentAttempt >= retries) throw e
                currentAttempt++
                delay(delayMs)
                delayMs *= 2 // exponential
            }
        }
    }

    /**
     * Default capability check – subclasses can override.
     */
    override fun supportsCapability(capability: String): Boolean {
        return capabilities().contains(capability)
    }

    /**
     * List of capabilities this provider supports.
     */
    protected abstract fun capabilities(): List<String>
}