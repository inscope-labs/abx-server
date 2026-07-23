package com.inscopelabs.abx.server

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.inscopelabs.abx.server.core.audit.AuditLog
import com.inscopelabs.abx.server.core.keystore.FingerprintUtils
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager
import com.inscopelabs.abx.server.core.session.SessionManager
import com.inscopelabs.abx.server.core.session.SessionManagerProvider
import com.inscopelabs.abx.server.core.session.SessionState
import com.inscopelabs.abx.server.core.session.UserGesture
import com.inscopelabs.abx.server.core.tunnel.TunnelService
import com.inscopelabs.abx.server.workspace.SecureNavigation
import com.inscopelabs.abx.server.workspace.SecureTab
import com.inscopelabs.abx.server.workspace.chat.ChatAdapter
import com.inscopelabs.abx.server.workspace.chat.ChatSettingsSheet
import com.inscopelabs.abx.server.workspace.chat.ChatUiState
import com.inscopelabs.abx.server.workspace.chat.ChatViewModel
import com.inscopelabs.abx.server.workspace.chat.ChatViewModelFactory
import com.inscopelabs.abx.server.workspace.widget.WorkspaceServerSwitch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.KeyPair

/**
 * Top-level Workspace view hosting "Home" (Dashboard posture & metrics) and
 * "Chat" tabs. Toggled with ServerFragment via WorkspaceServerSwitch.
 */
class WorkspaceFragment : Fragment(R.layout.fragment_workspace) {

    private companion object {
        private const val ALIAS = "abx_mcp_device_key"
    }

    private var secureNav: SecureNavigation? = null
    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var sessionManager: SessionManager
    private var currentSessionState: SessionState = SessionState.INACTIVE
    private var ttlRemaining = 0
    private var isHardwareBacked = false
    private var isStrongBoxBacked = false
    private var fingerprint: String = ""

    private val viewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(requireActivity().application)
    }

    private val adapter = ChatAdapter()

    private var lastRenderedMessageCount = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        secureNav = context as? SecureNavigation
    }

    override fun onDetach() {
        super.onDetach()
        secureNav = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switch = view.findViewById<WorkspaceServerSwitch>(R.id.workspaceServerSwitch)
        switch?.setInitialPhase(WorkspaceServerSwitch.Phase.WORKSPACE)
        switch?.onPhaseChanged = {
            (activity as? MainActivity)?.switchTopLevelWorkspace(MainActivity.Workspace.SERVER)
        }

        setupSwipeToToolbox(view)

        initDependencies()
        setupTabs(view)
        bindHomeViews(view)
        observeSessionState()
        loadKeyMaterial()

        // Chat setup
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

    override fun onResume() {
        super.onResume()
        refreshHomeTab()
    }

    private fun initDependencies() {
        keyStoreManager = KeyStoreManager(requireContext().applicationContext)
        sessionManager = SessionManagerProvider.get(requireContext().applicationContext)
    }

    private fun setupTabs(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.workspaceTabLayout)
        val containerHome = view.findViewById<View>(R.id.containerHomeTab)
        val containerChat = view.findViewById<View>(R.id.containerChatTab)
        val newSessionButton = view.findViewById<ImageButton>(R.id.chatNewSessionButton)
        val settingsButton = view.findViewById<ImageButton>(R.id.chatSettingsButton)

        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_home)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_chat)))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                val isHome = position == 0
                containerHome.visibility = if (isHome) View.VISIBLE else View.GONE
                containerChat.visibility = if (isHome) View.GONE else View.VISIBLE
                newSessionButton.visibility = if (isHome) View.GONE else View.VISIBLE
                settingsButton.visibility = if (isHome) View.GONE else View.VISIBLE
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Default to Home tab
        containerHome.visibility = View.VISIBLE
        containerChat.visibility = View.GONE
        newSessionButton.visibility = View.GONE
        settingsButton.visibility = View.GONE
    }

    private fun bindHomeViews(view: View) {
        view.findViewById<MaterialButton>(R.id.btnDashToggleSession)?.setOnClickListener {
            toggleSession()
        }

        view.findViewById<MaterialButton>(R.id.btnNavAccess)?.setOnClickListener {
            secureNav?.openSecureTab(SecureTab.ACCESS)
        }

        view.findViewById<MaterialButton>(R.id.btnNavRemove)?.setOnClickListener {
            secureNav?.openSecureTab(SecureTab.REMOVE)
        }

        view.findViewById<MaterialButton>(R.id.btnNavActivity)?.setOnClickListener {
            secureNav?.openSecureTab(SecureTab.ACTIVITY)
        }
    }

    private fun observeSessionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            sessionManager.stateFlow.collectLatest { state ->
                currentSessionState = state
                if (state is SessionState.ACTIVE) {
                    ttlRemaining = sessionManager.getSessionTtl()
                } else {
                    ttlRemaining = 0
                }
                refreshHomeTab()
            }
        }
    }

    private fun toggleSession() {
        if (currentSessionState is SessionState.ACTIVE) {
            sessionManager.stopSession()
        } else {
            sessionManager.startSession(UserGesture.LocalButtonPress)
            TunnelService.start(requireContext().applicationContext)
        }
    }

    private fun loadKeyMaterial() {
        try {
            val kp = keyStoreManager.getOrCreateKeyPair(ALIAS)
            val rawFp = FingerprintUtils.getFingerprint(kp.public)
            fingerprint = rawFp
            updateHardwareStatus(kp)
            refreshHomeTab()
        } catch (e: Exception) {
            fingerprint = ""
        }
    }

    private fun updateHardwareStatus(kp: KeyPair) {
        if (keyStoreManager.isAndroidKeyStore) {
            try {
                val keyFactory = KeyFactory.getInstance(kp.private.algorithm, "AndroidKeyStore")
                val keyInfo = keyFactory.getKeySpec(kp.private, KeyInfo::class.java) as KeyInfo
                isHardwareBacked = keyInfo.isInsideSecureHardware
                isStrongBoxBacked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
                } else {
                    false
                }
            } catch (e: Exception) {
                isHardwareBacked = false
                isStrongBoxBacked = false
            }
        } else {
            isHardwareBacked = false
            isStrongBoxBacked = false
        }
    }

    private fun refreshHomeTab() {
        val root = view ?: return
        val isActive = currentSessionState is SessionState.ACTIVE
        val isChainSecure = AuditLog.verifyIntegrity()

        // Enclave Security
        val txtEnclave = root.findViewById<TextView>(R.id.txtDashEnclaveSecurity)
        txtEnclave?.text = if (isStrongBoxBacked) "StrongBox Secured" else if (isHardwareBacked) "TEE Secured" else "Software Sandbox"

        // Audit Integrity
        val txtAudit = root.findViewById<TextView>(R.id.txtDashAuditIntegrity)
        txtAudit?.text = if (isChainSecure) "SECURE" else "TAMPERED"
        txtAudit?.setTextColor(ContextCompat.getColor(requireContext(), if (isChainSecure) R.color.color_success else R.color.color_error))

        // TTL
        val txtTtl = root.findViewById<TextView>(R.id.txtDashTtl)
        val txtSub = root.findViewById<TextView>(R.id.txtDashSessionSub)
        val btnToggle = root.findViewById<MaterialButton>(R.id.btnDashToggleSession)

        if (isActive) {
            txtTtl?.text = "${ttlRemaining}s remaining"
            txtSub?.text = "Secure attestation ticket active"
            btnToggle?.text = getString(R.string.btn_stop_session)
            btnToggle?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_error))
        } else {
            txtTtl?.text = "Inactive"
            txtSub?.text = "Start session in Access tab"
            btnToggle?.text = getString(R.string.btn_start_session)
            btnToggle?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_primary))
        }

        // Fingerprint snippet
        val txtFpSnippet = root.findViewById<TextView>(R.id.txtDashFingerprintSnippet)
        if (fingerprint.length >= 12) {
            txtFpSnippet?.text = "${fingerprint.take(6)}...${fingerprint.takeLast(6)}"
        } else {
            txtFpSnippet?.text = "Keys active"
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

        val toolbar: Toolbar = view.findViewById(R.id.chatToolbar)
        toolbar.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }
}
