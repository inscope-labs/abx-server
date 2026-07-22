package com.inscopelabs.abx.server.workspace.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI-facing chat state for a single (the current) session.
 *
 * [messages] is the persisted history from ChatManager.sessions PLUS, while
 * a response is streaming, one synthetic trailing Message built from
 * accumulated chunks — ChatManager only calls repository.addMessage() once
 * the full response has finished, so without this the UI would show
 * nothing at all while the model is "typing".
 */
data class ChatUiState(
    val sessionId: String? = null,
    val provider: String = "gemini",
    val model: String = "gemini-2.5-flash",
    val messages: List<Message> = emptyList(),
    val streamingState: StreamingState = StreamingState.IDLE,
    val hasApiKey: Boolean = true,
    val errorMessage: String? = null,
    val initialized: Boolean = false
) {
    val isStreaming: Boolean
        get() = streamingState == StreamingState.CONNECTING ||
            streamingState == StreamingState.RECEIVING ||
            streamingState == StreamingState.RETRYING ||
            streamingState == StreamingState.THINKING
}

class ChatViewModel(
    application: Application,
    private val chatManager: ChatManager,
    private val chatRepository: ChatRepository,
    private val chatSecurity: ChatSecurity
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Accumulates StreamingChunk text for the response currently in flight.
    // Cleared once the corresponding MessageAdded/terminal StateChanged
    // event lands (at which point the persisted list already has it).
    private val draftBuffer = StringBuilder()

    companion object {
        private const val DRAFT_MESSAGE_ID = "streaming-draft"
    }

    init {
        viewModelScope.launch {
            chatManager.events.collect { event -> onChatEvent(event) }
        }
        viewModelScope.launch {
            bootstrapSession()
        }
    }

    private suspend fun bootstrapSession() {
        // One-shot direct read from Room (see ChatDependencies.chatRepository
        // doc) to avoid racing ChatManager's own internal StateFlow
        // subscription, which starts at emptyList().
        val existing = chatRepository.observeSessions().first()
        val session = existing.maxByOrNull { it.updatedAt } ?: chatManager.createSession()

        _uiState.value = _uiState.value.copy(
            sessionId = session.id,
            provider = session.provider,
            model = session.model,
            messages = session.messages,
            hasApiKey = hasApiKey(session.provider),
            initialized = true
        )

        // Keep messages (and provider/model, in case they're changed
        // elsewhere) in sync with persisted state for this session.
        viewModelScope.launch {
            chatManager.sessions.collect { sessions ->
                val current = _uiState.value.sessionId ?: return@collect
                val match = sessions.find { it.id == current } ?: return@collect
                _uiState.value = _uiState.value.copy(
                    provider = match.provider,
                    model = match.model,
                    messages = mergeWithDraft(match.messages),
                    hasApiKey = hasApiKey(match.provider)
                )
            }
        }
    }

    private fun mergeWithDraft(persisted: List<Message>): List<Message> {
        if (draftBuffer.isEmpty()) return persisted
        val draft = Message(
            // Stable id across chunks of the same turn — Message.id
            // defaults to a fresh random UUID per instance, which would
            // make ChatAdapter's DiffUtil treat every single chunk update
            // as "remove old item, insert new item" instead of an in-place
            // content update, causing jank/scroll-jump on every token.
            id = DRAFT_MESSAGE_ID,
            role = MessageRole.ASSISTANT,
            content = draftBuffer.toString(),
            status = MessageStatus.STREAMING
        )
        return persisted + draft
    }

    private fun onChatEvent(event: ChatEvent) {
        val sessionId = _uiState.value.sessionId ?: return
        when (event) {
            is ChatEvent.StreamingChunk -> {
                if (event.sessionId != sessionId) return
                draftBuffer.append(event.chunk)
                _uiState.value = _uiState.value.copy(
                    messages = mergeWithDraft(persistedMessagesOnly()),
                    errorMessage = null
                )
            }
            is ChatEvent.StateChanged -> {
                if (event.sessionId != sessionId) return
                when (event.state) {
                    StreamingState.DONE -> {
                        // Deliberately do NOT rebuild `messages` here. The
                        // draft bubble already holds the complete final text,
                        // and ChatManager did persist it — clearing the
                        // buffer and re-merging immediately would make the
                        // bubble vanish for one frame until the sessions
                        // Flow (Room) catches up. Leaving the draft in place
                        // until the sessions collector naturally supersedes
                        // it gives a seamless swap instead of a flicker.
                        draftBuffer.setLength(0)
                    }
                    StreamingState.ERROR, StreamingState.CANCELLED -> {
                        // Unlike DONE, ChatManager never persists anything
                        // for a failed/cancelled turn — so nothing will ever
                        // arrive from the sessions Flow to supersede this
                        // draft. Freeze whatever partial text came in as its
                        // own message instead of leaving it stuck rendering
                        // as "still streaming" forever.
                        val partial = draftBuffer.toString()
                        draftBuffer.setLength(0)
                        val base = persistedMessagesOnly()
                        _uiState.value = _uiState.value.copy(
                            messages = if (partial.isNotBlank()) {
                                base + Message(
                                    role = MessageRole.ASSISTANT,
                                    content = partial,
                                    status = if (event.state == StreamingState.ERROR) {
                                        MessageStatus.ERROR
                                    } else {
                                        MessageStatus.SENT
                                    }
                                )
                            } else {
                                base
                            }
                        )
                    }
                    else -> { /* no message-list change for non-terminal states */ }
                }
                _uiState.value = _uiState.value.copy(streamingState = event.state)
            }
            is ChatEvent.ErrorOccurred -> {
                if (event.sessionId != sessionId) return
                _uiState.value = _uiState.value.copy(
                    errorMessage = event.throwable.message ?: "Something went wrong"
                )
            }
            is ChatEvent.MessageAdded, is ChatEvent.SessionUpdated,
            is ChatEvent.SessionCreated, is ChatEvent.SessionDeleted,
            is ChatEvent.ConversationCleared -> {
                // These are reflected via chatManager.sessions collection above.
            }
        }
    }

    private fun persistedMessagesOnly(): List<Message> {
        // Strip any previously-merged draft before re-merging with the
        // latest buffer contents, so we don't double-append.
        val current = _uiState.value.messages
        return if (current.isNotEmpty() && current.last().status == MessageStatus.STREAMING) {
            current.dropLast(1)
        } else {
            current
        }
    }

    fun hasApiKey(provider: String): Boolean = !chatSecurity.getApiKey(provider).isNullOrBlank()

    fun saveApiKey(provider: String, apiKey: String) {
        chatSecurity.storeApiKey(provider, apiKey)
        _uiState.value = _uiState.value.copy(hasApiKey = hasApiKey(provider))
    }

    fun send(text: String) {
        val trimmed = ChatUtils.sanitizeInput(text)
        if (trimmed.isEmpty()) return
        val sessionId = _uiState.value.sessionId ?: return
        if (!hasApiKey(_uiState.value.provider)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Add an API key for ${_uiState.value.provider} before sending a message."
            )
            return
        }
        chatManager.send(sessionId, trimmed)
    }

    fun cancelStreaming() {
        val sessionId = _uiState.value.sessionId ?: return
        chatManager.cancel(sessionId)
    }

    fun switchProvider(provider: String, model: String) {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch { chatManager.switchProvider(sessionId, provider, model) }
    }

    fun startNewSession() {
        viewModelScope.launch {
            val session = chatManager.createSession(
                provider = _uiState.value.provider,
                model = _uiState.value.model
            )
            draftBuffer.setLength(0)
            _uiState.value = _uiState.value.copy(
                sessionId = session.id,
                messages = emptyList(),
                streamingState = StreamingState.IDLE,
                errorMessage = null
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}

class ChatViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val chatManager = ChatDependencies.chatManager(application)
        val chatRepository = ChatDependencies.chatRepository(application)
        val chatSecurity = ChatDependencies.chatSecurity(application)
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(application, chatManager, chatRepository, chatSecurity) as T
    }
}
