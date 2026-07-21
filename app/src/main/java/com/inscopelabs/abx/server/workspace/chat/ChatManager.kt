package com.inscopelabs.abx.server.workspace.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class ChatManager(
    private val repository: ChatRepository,
    private val providerFactory: ProviderFactory,
    private val promptBuilder: PromptBuilder,
    private val tokenCounter: TokenCounter,
    private val chatMemory: ChatMemory,
    private val chatLogger: ChatLogger,
    private val chatSecurity: ChatSecurity,
    private val chatCache: ChatCache,
    private val chatExport: ChatExport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    // Active streaming jobs per session
    private val activeJobs = mutableMapOf<String, Job>()

    // Event bus for UI/other components
    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 64)
    val events: Flow<ChatEvent> = _events.asSharedFlow()

    // Expose sessions as StateFlow for easy observation
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    init {
        scope.launch {
            repository.observeSessions().collect { sessionList ->
                _sessions.value = sessionList
            }
        }
    }

    /**
     * Create a new chat session with default settings.
     */
    suspend fun createSession(
        title: String = "New Chat",
        provider: String = "gemini",
        model: String = "gemini-1.5-pro"
    ): ChatSession {
        val session = ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            provider = provider,
            model = model,
            settings = ChatSettings(provider = provider, model = model)
        )
        repository.saveSession(session)
        _events.emit(ChatEvent.SessionCreated(session))
        return session
    }

    /**
     * Delete a session and cancel any ongoing request.
     */
    suspend fun deleteSession(sessionId: String) {
        cancel(sessionId)
        repository.deleteSession(sessionId)
        _events.emit(ChatEvent.SessionDeleted(sessionId))
    }

    /**
     * Send a prompt to the AI provider for the given session.
     * The result is streamed via [events] and persisted.
     */
    fun send(
        sessionId: String,
        prompt: String,
        workspaceContext: String = "",
        attachments: List<Attachment> = emptyList()
    ) {
        // Cancel any previous job for this session
        cancel(sessionId)

        val job = scope.launch {
            val session = repository.getSession(sessionId) ?: run {
                chatLogger.logError("Session not found", IllegalArgumentException("Session $sessionId not found"))
                return@launch
            }

            // Validate API key
            val apiKey = chatSecurity.getApiKey(session.provider)
            if (apiKey.isNullOrBlank()) {
                _events.emit(ChatEvent.ErrorOccurred(sessionId, SecurityException("API key not set for ${session.provider}")))
                _events.emit(ChatEvent.StateChanged(sessionId, StreamingState.ERROR))
                return@launch
            }

            // Create user message
            val userMessage = Message(
                role = MessageRole.USER,
                content = prompt,
                attachments = attachments,
                tokenCount = tokenCounter.countTokens(prompt)
            )
            repository.addMessage(sessionId, userMessage)
            _events.emit(ChatEvent.MessageAdded(sessionId, userMessage))

            // Prepare provider and streaming
            val provider = providerFactory.createProvider(session.provider, apiKey)
            val settings = session.settings

            // Enforce memory budget on history (trim if needed)
            val trimmedHistory = chatMemory.enforceBudget(
                session.messages + userMessage,
                settings.maxTokens
            )

            // Build the final prompt (with workspace context, memory, etc.)
            val finalPrompt = promptBuilder.build(
                systemPrompt = "You are a helpful AI assistant integrated with the ABX Server workspace.",
                history = trimmedHistory,
                workspaceContext = workspaceContext,
                userPrompt = prompt,
                attachments = attachments,
                maxTokens = settings.maxTokens,
                memorySummary = chatMemory.summarizeContext(session.messages)
            )

            // Check cache for identical prompt (optional)
            val cacheKey = ChatUtils.generateChecksum(finalPrompt)
            chatCache.get(cacheKey)?.let { cachedResponse ->
                // Emit cached response as a single chunk
                _events.emit(ChatEvent.StreamingChunk(sessionId, cachedResponse))
                val assistantMsg = Message(
                    role = MessageRole.ASSISTANT,
                    content = cachedResponse,
                    tokenCount = tokenCounter.countTokens(cachedResponse)
                )
                repository.addMessage(sessionId, assistantMsg)
                _events.emit(ChatEvent.MessageAdded(sessionId, assistantMsg))
                _events.emit(ChatEvent.StateChanged(sessionId, StreamingState.DONE))
                return@launch
            }

            // Log request
            chatLogger.logRequest(session.provider, session.model, finalPrompt.length)

            // Emit connecting state
            _events.emit(ChatEvent.StateChanged(sessionId, StreamingState.CONNECTING))

            val startTime = System.currentTimeMillis()
            val fullResponse = StringBuilder()

            try {
                // Perform the actual streaming call with timeout and retries
                var attempt = 0
                var lastError: Throwable? = null
                while (attempt < settings.retryCount) {
                    try {
                        withTimeoutOrNull(settings.timeoutMillis) {
                            provider.sendMessage(finalPrompt, settings).collect { chunk ->
                                fullResponse.append(chunk)
                                _events.emit(ChatEvent.StreamingChunk(sessionId, chunk))
                            }
                        } ?: throw TimeoutException("Request timed out after ${settings.timeoutMillis}ms")
                        break // Success
                    } catch (e: Exception) {
                        lastError = e
                        attempt++
                        if (attempt < settings.retryCount) {
                            chatLogger.logError(session.provider, e)
                            _events.emit(ChatEvent.StateChanged(sessionId, StreamingState.RETRYING))
                            // Exponential backoff
                            kotlinx.coroutines.delay(1000L * (1L shl attempt))
                        } else {
                            throw e
                        }
                    }
                }

                // If we got here, we have a successful response
                val responseText = fullResponse.toString()
                // Cache the response for identical prompts
                chatCache.put(cacheKey, responseText)

                val assistantMsg = Message(
                    role = MessageRole.ASSISTANT,
                    content = responseText,
                    tokenCount = tokenCounter.countTokens(responseText)
                )
                repository.addMessage(sessionId, assistantMsg)
                _events.emit(ChatEvent.MessageAdded(sessionId, assistantMsg))
                _events.emit(ChatEvent.StateChanged(sessionId, StreamingState.DONE))

                val latency = System.currentTimeMillis() - startTime
                chatLogger.logResponse(session.provider, latency, assistantMsg.tokenCount)

            } catch (e: Exception) {
                chatLogger.logError(session.provider, e)
                _events.emit(ChatEvent.StateChanged(sessionId, StreamingState.ERROR))
                _events.emit(ChatEvent.ErrorOccurred(sessionId, e))
            }
        }

        activeJobs[sessionId] = job
        job.invokeOnCompletion { activeJobs.remove(sessionId) }
    }

    /**
     * Cancel the ongoing request for a session.
     */
    fun cancel(sessionId: String) {
        activeJobs[sessionId]?.cancel()
        activeJobs.remove(sessionId)
        chatLogger.logCancellation(sessionId)
        scope.launch {
            _events.emit(ChatEvent.StateChanged(sessionId, StreamingState.CANCELLED))
        }
    }

    /**
     * Switch the AI provider/model for a session.
     */
    suspend fun switchProvider(sessionId: String, newProvider: String, newModel: String) {
        val session = repository.getSession(sessionId) ?: return
        val updated = session.copy(
            provider = newProvider,
            model = newModel,
            settings = session.settings.copy(provider = newProvider, model = newModel),
            updatedAt = System.currentTimeMillis()
        )
        repository.saveSession(updated)
        _events.emit(ChatEvent.SessionUpdated(updated))
    }

    /**
     * Update session settings (temperature, maxTokens, etc.)
     */
    suspend fun updateSettings(sessionId: String, settings: ChatSettings) {
        val session = repository.getSession(sessionId) ?: return
        val updated = session.copy(settings = settings, updatedAt = System.currentTimeMillis())
        repository.saveSession(updated)
    }

    /**
     * Export a session to the given format.
     */
    suspend fun export(sessionId: String, format: String = "markdown"): String {
        val session = repository.getSession(sessionId) ?: throw IllegalArgumentException("Session not found")
        return chatExport.exportSession(session, format)
    }

    /**
     * Clear all messages from a session (but keep the session).
     */
    suspend fun clearConversation(sessionId: String) {
        val session = repository.getSession(sessionId) ?: return
        val updated = session.copy(messages = emptyList(), updatedAt = System.currentTimeMillis())
        repository.saveSession(updated)
        _events.emit(ChatEvent.ConversationCleared(sessionId))
    }

    /**
     * Delete all sessions (clear everything).
     */
    suspend fun deleteAllSessions() {
        val all = _sessions.value
        all.forEach { cancel(it.id) }
        repository.deleteAll()
        // _sessions will be updated by the repository flow
    }
}