package com.inscopelabs.abx.server

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.inscopelabs.abx.server.workspace.chat.ChatAdapter
import com.inscopelabs.abx.server.workspace.chat.ChatSettingsSheet
import com.inscopelabs.abx.server.workspace.chat.ChatUiState
import com.inscopelabs.abx.server.workspace.chat.ChatViewModel
import com.inscopelabs.abx.server.workspace.chat.ChatViewModelFactory
import kotlinx.coroutines.launch

/**
 * One of the two main user views. Renders at the exact same coordinates as
 * FilesFragment (both fill mainContentContainer) — toggled via the
 * Chat/Files switch, never shown simultaneously with FilesFragment.
 *
 * Wires the previously-unbuilt UI layer to the existing chat subsystem
 * (workspace/chat): ChatManager for send/stream/persist, ChatSecurity
 * for the API key, ChatAdapter for rendering.
 */
class ChatFragment : Fragment(R.layout.fragment_chat) {

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(requireActivity().application)
    }

    private val adapter = ChatAdapter()

    private var lastRenderedMessageCount = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSwipeToToolbox(view)

        val messageList: RecyclerView = view.findViewById(R.id.chatMessageList)
        val emptyState: LinearLayout = view.findViewById(R.id.chatEmptyState)
        val typingIndicator: TextView = view.findViewById(R.id.chatTypingIndicator)
        val apiKeyBanner: LinearLayout = view.findViewById(R.id.chatApiKeyBanner)
        val apiKeyBannerDivider: View = view.findViewById(R.id.chatApiKeyBannerDivider)
        val inputEditText: EditText = view.findViewById(R.id.chatInputEditText)
        val sendButton: ImageButton = view.findViewById(R.id.chatSendButton)
        val newSessionButton: ImageButton = view.findViewById(R.id.chatNewSessionButton)
        val settingsButton: ImageButton = view.findViewById(R.id.chatSettingsButton)

        val layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        messageList.layoutManager = layoutManager
        messageList.adapter = adapter

        val openSettings: () -> Unit = {
            val state = viewModel.uiState.value
            val sheet = ChatSettingsSheet.newInstance(state.provider, state.model)
            sheet.onSave = { provider, model, _ ->
                viewModel.switchProvider(provider, model)
            }
            sheet.show(childFragmentManager, "chat_settings")
        }

        settingsButton.setOnClickListener { openSettings() }
        apiKeyBanner.setOnClickListener { openSettings() }

        newSessionButton.setOnClickListener {
            inputEditText.setText("")
            viewModel.startNewSession()
        }

        sendButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isStreaming) {
                viewModel.cancelStreaming()
                return@setOnClickListener
            }
            val text = inputEditText.text.toString()
            if (text.isBlank()) return@setOnClickListener
            viewModel.send(text)
            inputEditText.setText("")
            hideKeyboard(inputEditText)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(
                        state = state,
                        messageList = messageList,
                        emptyState = emptyState,
                        typingIndicator = typingIndicator,
                        apiKeyBanner = apiKeyBanner,
                        apiKeyBannerDivider = apiKeyBannerDivider,
                        sendButton = sendButton
                    )
                }
            }
        }
    }

    private fun render(
        state: ChatUiState,
        messageList: RecyclerView,
        emptyState: LinearLayout,
        typingIndicator: TextView,
        apiKeyBanner: LinearLayout,
        apiKeyBannerDivider: View,
        sendButton: ImageButton
    ) {
        adapter.submitList(state.messages) {
            // Auto-scroll only when new content actually arrived, so we
            // don't fight the user if they've scrolled up to re-read
            // something earlier in a long conversation.
            if (state.messages.size != lastRenderedMessageCount || state.isStreaming) {
                lastRenderedMessageCount = state.messages.size
                if (state.messages.isNotEmpty()) {
                    messageList.scrollToPosition(state.messages.size - 1)
                }
            }
        }

        emptyState.visibility = if (state.initialized && state.messages.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        typingIndicator.visibility = if (state.isStreaming) View.VISIBLE else View.GONE

        val showBanner = state.initialized && !state.hasApiKey
        apiKeyBanner.visibility = if (showBanner) View.VISIBLE else View.GONE
        apiKeyBannerDivider.visibility = if (showBanner) View.VISIBLE else View.GONE

        sendButton.setImageResource(if (state.isStreaming) R.drawable.ic_stop_circle else R.drawable.ic_send)
        sendButton.contentDescription = getString(
            if (state.isStreaming) R.string.chat_cancel else R.string.chat_send
        )
        sendButton.isEnabled = state.initialized

        state.errorMessage?.let { message ->
            com.google.android.material.snackbar.Snackbar
                .make(sendButton.rootView, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .show()
            viewModel.dismissError()
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupSwipeToToolbox(view: View) {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (diffY > 150 && Math.abs(velocityY) > 150) {
                        (activity as? MainActivity)?.openToolbox()
                        return true
                    }
                }
                return false
            }
        })

        // Only the top bar participates in the swipe-down-to-toolbox
        // gesture; the message list and input need their touch events for
        // scrolling/typing. Matches the same trigger area convention as
        // FilesFragment.
        val toolbar: Toolbar = view.findViewById(R.id.chatToolbar)
        toolbar.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }
}
