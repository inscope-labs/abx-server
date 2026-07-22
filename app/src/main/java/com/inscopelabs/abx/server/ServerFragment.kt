package com.inscopelabs.abx.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.inscopelabs.abx.server.core.audit.AuditLog
import com.inscopelabs.abx.server.core.audit.ReasonCode
import com.inscopelabs.abx.server.core.diagnostics.CrashReporterManager
import com.inscopelabs.abx.server.core.diagnostics.DiagnosticBundle
import com.inscopelabs.abx.server.core.diagnostics.DiagnosticExporter
import com.inscopelabs.abx.server.core.diagnostics.LogViewerActivity
import com.inscopelabs.abx.server.core.keystore.FingerprintUtils
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager
import com.inscopelabs.abx.server.core.mcp.FileSystemReaderImpl
import com.inscopelabs.abx.server.core.mcp.McpExecutor
import com.inscopelabs.abx.server.core.policy.Capability
import com.inscopelabs.abx.server.core.policy.PolicyEngineImpl
import com.inscopelabs.abx.server.core.session.SessionManager
import com.inscopelabs.abx.server.core.session.SessionManagerProvider
import com.inscopelabs.abx.server.core.session.SessionState
import com.inscopelabs.abx.server.core.session.UserGesture
import com.inscopelabs.abx.server.core.tunnel.TunnelService
import com.inscopelabs.abx.server.workspace.SecureTab
import com.inscopelabs.abx.server.workspace.widget.WorkspaceServerSwitch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Primary workspace fragment for secure-server management in XML/Views.
 */
class ServerFragment : Fragment(R.layout.fragment_server) {

    companion object {
        private const val ALIAS = "abx_mcp_device_key"
        private const val ARG_TAB = "arg_secure_tab"

        fun newInstance(tab: SecureTab = SecureTab.CONNECT): ServerFragment {
            val fragment = ServerFragment()
            val args = Bundle()
            args.putString(ARG_TAB, tab.name)
            fragment.arguments = args
            return fragment
        }
    }

    fun selectTab(tab: SecureTab) {
        val position = when (tab) {
            SecureTab.CONNECT -> 0
            SecureTab.ACCESS -> 1
            SecureTab.REMOVE -> 2
            SecureTab.ACTIVITY -> 3
        }
        val tabLayout = view?.findViewById<TabLayout>(R.id.filesTabLayout)
        tabLayout?.getTabAt(position)?.select()
    }

    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var sessionManager: SessionManager
    private lateinit var policyEngine: PolicyEngineImpl
    private lateinit var mcpExecutor: McpExecutor

    private var currentKeyPair: KeyPair? = null
    private var fingerprint: String = ""
    private var formattedFingerprint: String = ""
    private var isFingerprintExpanded = false
    private var isHardwareBacked = false
    private var isStrongBoxBacked = false
    private var gatewayPairedStatus = "Not Paired (Local Mode Only)"
    private var currentSessionState: SessionState = SessionState.INACTIVE
    private var ttlRemaining = 0
    private var timerJob: Job? = null

    // Container views
    private lateinit var containerConnect: LinearLayout
    private lateinit var containerAccess: LinearLayout
    private lateinit var containerRemove: LinearLayout
    private lateinit var containerActivity: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switch = view.findViewById<WorkspaceServerSwitch>(R.id.workspaceServerSwitch)
        switch?.setInitialPhase(WorkspaceServerSwitch.Phase.SERVER)
        switch?.onPhaseChanged = {
            (activity as? MainActivity)?.switchTopLevelWorkspace(MainActivity.Workspace.WORKSPACE)
        }

        initDependencies()
        setupGestureDetector(view)
        setupTabs(view)
        bindViews(view)
        observeSessionState()
        loadKeyMaterial()
    }

    override fun onResume() {
        super.onResume()
        checkSharedTextIntent()
        refreshUI()
    }

    private fun initDependencies() {
        keyStoreManager = KeyStoreManager(requireContext().applicationContext)
        sessionManager = SessionManagerProvider.get(requireContext().applicationContext)
        policyEngine = PolicyEngineImpl()
        val fileSystemReader = FileSystemReaderImpl(requireContext().applicationContext)
        mcpExecutor = McpExecutor(policyEngine, fileSystemReader)
    }

    private fun setupGestureDetector(view: View) {
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

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun setupTabs(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.filesTabLayout)
        containerConnect = view.findViewById(R.id.containerConnectTab)
        containerAccess = view.findViewById(R.id.containerAccessTab)
        containerRemove = view.findViewById(R.id.containerRemoveTab)
        containerActivity = view.findViewById(R.id.containerActivityTab)

        val tabTitles = arrayOf(
            getString(R.string.tab_connect),
            getString(R.string.tab_access),
            getString(R.string.tab_remove),
            getString(R.string.tab_activity)
        )

        tabLayout.removeAllTabs()
        tabTitles.forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateTabVisibility(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val initialTabName = arguments?.getString(ARG_TAB) ?: SecureTab.CONNECT.name
        val initialTab = try { SecureTab.valueOf(initialTabName) } catch (e: Exception) { SecureTab.CONNECT }
        selectTab(initialTab)
    }

    private fun updateTabVisibility(position: Int) {
        containerConnect.visibility = if (position == 0) View.VISIBLE else View.GONE
        containerAccess.visibility = if (position == 1) View.VISIBLE else View.GONE
        containerRemove.visibility = if (position == 2) View.VISIBLE else View.GONE
        containerActivity.visibility = if (position == 3) View.VISIBLE else View.GONE

        if (position == 3) {
            renderAuditLogs()
        }
    }

    private fun bindViews(view: View) {
        // --- Connect Tab ---
        view.findViewById<MaterialButton>(R.id.btnConnectCopyFingerprint).setOnClickListener {
            copyFingerprintToClipboard()
        }
        view.findViewById<MaterialButton>(R.id.btnConnectToggleFingerprint).setOnClickListener {
            isFingerprintExpanded = !isFingerprintExpanded
            refreshFingerprintViews()
        }
        view.findViewById<MaterialButton>(R.id.btnSimulateEnrollment).setOnClickListener {
            showPairingDialog()
        }
        view.findViewById<MaterialButton>(R.id.btnRotateKey).setOnClickListener {
            rotateKeyPair()
        }
        view.findViewById<MaterialButton>(R.id.btnClearCredentials).setOnClickListener {
            clearCredentials()
        }

        // --- Access Tab ---
        view.findViewById<MaterialButton>(R.id.btnAccessStartStopSession).setOnClickListener {
            toggleSession()
        }
        view.findViewById<MaterialButton>(R.id.btnAccessLocalBridge).setOnClickListener {
            showLocalBridgeDialog()
        }
        val switchAccessAdvanced = view.findViewById<SwitchMaterial>(R.id.switchAccessAdvanced)
        val txtAccessCapabilityJson = view.findViewById<TextView>(R.id.txtAccessCapabilityJson)
        switchAccessAdvanced.setOnCheckedChangeListener { _, isChecked ->
            txtAccessCapabilityJson.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                txtAccessCapabilityJson.text = generateMockCapabilityJson()
            }
        }

        // --- Remove Tab ---
        view.findViewById<MaterialButton>(R.id.btnWipeDataCompliance).setOnClickListener {
            clearCredentials()
        }

        // --- Activity Tab ---
        view.findViewById<MaterialButton>(R.id.btnSimulateExpired).setOnClickListener {
            AuditLog.recordRejection(ReasonCode.SESSION_EXPIRED, "session_mock", "Simulated session expired validation")
            renderAuditLogs()
        }
        view.findViewById<MaterialButton>(R.id.btnSimulateOutOfBounds).setOnClickListener {
            AuditLog.recordRejection(ReasonCode.PATH_OUT_OF_BOUNDS, "session_mock", "Simulated out of bounds validation")
            renderAuditLogs()
        }
        val switchActivityAdvanced = view.findViewById<SwitchMaterial>(R.id.switchActivityAdvanced)
        switchActivityAdvanced.setOnCheckedChangeListener { _, _ ->
            renderAuditLogs()
        }
    }

    private fun observeSessionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            sessionManager.stateFlow.collectLatest { state ->
                currentSessionState = state
                if (state is SessionState.ACTIVE) {
                    ttlRemaining = sessionManager.getSessionTtl()
                    startTimer()
                } else {
                    timerJob?.cancel()
                    ttlRemaining = 0
                }
                refreshSessionViews()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (currentSessionState is SessionState.ACTIVE) {
                delay(1000L)
                val nextTtl = sessionManager.decrementTtl(1)
                ttlRemaining = nextTtl
                refreshSessionViews()
                if (nextTtl <= 0) {
                    sessionManager.expireSession()
                    break
                }
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
            currentKeyPair = kp
            val rawFp = FingerprintUtils.getFingerprint(kp.public)
            fingerprint = rawFp
            formattedFingerprint = FingerprintUtils.formatFingerprint(rawFp)
            updateHardwareStatus(kp)
            refreshUI()
        } catch (e: Exception) {
            currentKeyPair = null
            fingerprint = ""
            formattedFingerprint = ""
            Toast.makeText(requireContext(), getString(R.string.msg_init_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun updateHardwareStatus(kp: KeyPair) {
        if (keyStoreManager.isAndroidKeyStore) {
            try {
                val keyFactory = KeyFactory.getInstance(kp.private.algorithm, "AndroidKeyStore")
                val keyInfo = keyFactory.getKeySpec(kp.private, KeyInfo::class.java) as KeyInfo
                isHardwareBacked = keyInfo.isInsideSecureHardware
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    isStrongBoxBacked = keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
                } else {
                    isStrongBoxBacked = false
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

    private fun rotateKeyPair() {
        try {
            val kp = keyStoreManager.generateKeyPair(ALIAS)
            currentKeyPair = kp
            val rawFp = FingerprintUtils.getFingerprint(kp.public)
            fingerprint = rawFp
            formattedFingerprint = FingerprintUtils.formatFingerprint(rawFp)
            updateHardwareStatus(kp)
            refreshUI()
            Toast.makeText(requireContext(), getString(R.string.msg_rotated_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Rotation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearCredentials() {
        try {
            keyStoreManager.deleteKeyPair(ALIAS)
            currentKeyPair = null
            fingerprint = ""
            formattedFingerprint = ""
            isHardwareBacked = false
            isStrongBoxBacked = false
            gatewayPairedStatus = "Not Paired (Local Mode Only)"
            sessionManager.stopSession()
            refreshUI()
            Toast.makeText(requireContext(), getString(R.string.msg_keys_cleared), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSharedTextIntent() {
        val sharedText = (activity as? MainActivity)?.consumeSharedText()
        if (!sharedText.isNullOrBlank()) {
            showLocalBridgeDialog(sharedText)
        }
    }

    private fun refreshUI() {
        refreshSessionViews()
        refreshFingerprintViews()
        refreshConnectEnclaveViews()
    }

    private fun refreshSessionViews() {
        val root = view ?: return
        val isActive = currentSessionState is SessionState.ACTIVE

        // Access Tab Session Card
        val accessBadge = root.findViewById<TextView>(R.id.txtAccessSessionBadge)
        val accessTtl = root.findViewById<TextView>(R.id.txtAccessTtl)
        val accessBtn = root.findViewById<MaterialButton>(R.id.btnAccessStartStopSession)
        val accessPolicyDesc = root.findViewById<TextView>(R.id.txtAccessPolicyDesc)
        val accessPolicyDetails = root.findViewById<TextView>(R.id.txtAccessPolicyDetails)

        val activeColor = ContextCompat.getColor(requireContext(), R.color.color_success)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.color_on_surface_variant)

        if (isActive) {
            accessBadge?.text = "ACTIVE SESSION"
            accessBadge?.setTextColor(activeColor)
            accessTtl?.text = getString(R.string.ttl_display, ttlRemaining)
            accessBtn?.setText(R.string.btn_stop_session)
            accessBtn?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_error))

            accessPolicyDesc?.setText(R.string.policy_summary_desc)
            accessPolicyDetails?.visibility = View.VISIBLE
        } else {
            accessBadge?.text = "INACTIVE / EXPIRED"
            accessBadge?.setTextColor(inactiveColor)
            accessTtl?.setText(R.string.ttl_inactive)
            accessBtn?.setText(R.string.btn_start_session)
            accessBtn?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_primary))

            accessPolicyDesc?.setText(R.string.policy_inactive_desc)
            accessPolicyDetails?.visibility = View.GONE
        }
    }

    private fun refreshFingerprintViews() {
        val root = view ?: return
        val connectFp = root.findViewById<TextView>(R.id.txtConnectFingerprint)
        val btnToggle = root.findViewById<MaterialButton>(R.id.btnConnectToggleFingerprint)

        val truncated = getTruncatedFingerprint(formattedFingerprint)
        val displayFp = if (isFingerprintExpanded) formattedFingerprint else truncated

        connectFp?.text = displayFp
        btnToggle?.setText(if (isFingerprintExpanded) R.string.btn_hide_full else R.string.btn_view_full)

        // QR Code image
        val imgQr = root.findViewById<ImageView>(R.id.imgConnectQrCode)
        if (imgQr != null && currentKeyPair != null) {
            val pubBytes = currentKeyPair?.public?.encoded ?: byteArrayOf()
            val qrContent = "abx:enroll?fp=$fingerprint&pub=${Base64.encodeToString(pubBytes, Base64.NO_WRAP)}"
            val qrBitmap = generateQrCodeBitmap(qrContent, 360)
            if (qrBitmap != null) {
                imgQr.setImageBitmap(qrBitmap)
            }
        }
    }

    private fun refreshConnectEnclaveViews() {
        val root = view ?: return

        val provider = if (keyStoreManager.isAndroidKeyStore) getString(R.string.val_android_keystore) else getString(R.string.val_jvm_sandbox)
        val hardwareStr = if (isHardwareBacked) getString(R.string.val_hardware_backed_yes) else getString(R.string.val_hardware_backed_no)

        root.findViewById<TextView>(R.id.txtProviderBackend)?.text = "Provider Backend: $provider"
        root.findViewById<TextView>(R.id.txtKeyIsolation)?.text = "Key Isolation: " + getString(R.string.val_key_isolation_text)
        root.findViewById<TextView>(R.id.txtHardwareBacked)?.text = "Hardware Backed: $hardwareStr"
        root.findViewById<TextView>(R.id.txtStrongBoxSupport)?.text = "StrongBox Support: " + if (isStrongBoxBacked) getString(R.string.val_strongbox_yes) else "NO"
        root.findViewById<TextView>(R.id.txtGatewayStatus)?.text = gatewayPairedStatus
    }

    private fun copyFingerprintToClipboard() {
        if (formattedFingerprint.isNotEmpty()) {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ABX Public Key Fingerprint", formattedFingerprint)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), getString(R.string.msg_fingerprint_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLocalBridgeDialog(initialInput: String = "") {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_local_bridge, null)
        val editInput = dialogView.findViewById<EditText>(R.id.editBridgeInput)
        val btnToggleAdv = dialogView.findViewById<TextView>(R.id.btnToggleBridgeAdvanced)
        val containerAdv = dialogView.findViewById<LinearLayout>(R.id.containerBridgeAdvanced)
        val editRoots = dialogView.findViewById<EditText>(R.id.editBridgeRoots)
        val editOps = dialogView.findViewById<EditText>(R.id.editBridgeOps)
        val editMaxReq = dialogView.findViewById<EditText>(R.id.editBridgeMaxReq)
        val txtError = dialogView.findViewById<TextView>(R.id.txtBridgeError)
        val txtResult = dialogView.findViewById<TextView>(R.id.txtBridgeResult)

        editInput.setText(initialInput)

        val tempDir = System.getProperty("java.io.tmpdir") ?: ""
        val defaultRoots = "/storage/emulated/0/Download,/storage/emulated/0/Documents" + if (tempDir.isNotEmpty()) ",$tempDir" else ""
        editRoots.setText(defaultRoots)
        editOps.setText("read_file,write_file,list_directory,delete_file,append_file,file_exists,get_file_metadata,get_file_version")

        btnToggleAdv.setOnClickListener {
            val isVisible = containerAdv.visibility == View.VISIBLE
            containerAdv.visibility = if (isVisible) View.GONE else View.VISIBLE
            btnToggleAdv.text = if (isVisible) "▼ Advanced Capability Settings" else "▲ Advanced Capability Settings"
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Execute", null)
            .setNeutralButton("Copy Result", null)
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            val executeBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val copyBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)

            copyBtn.visibility = View.GONE

            executeBtn.setOnClickListener {
                txtError.visibility = View.GONE
                txtResult.visibility = View.GONE

                val inputText = editInput.text.toString()
                val rootsText = editRoots.text.toString()
                val opsText = editOps.text.toString()
                val maxReqText = editMaxReq.text.toString()

                executeLocalBridgeRequest(
                    inputText = inputText,
                    allowedRootsStr = rootsText,
                    allowedOpsStr = opsText,
                    maxReqStr = maxReqText,
                    onSuccess = { res ->
                        txtResult.text = extractBridgeDisplayResult(res)
                        txtResult.visibility = View.VISIBLE
                        copyBtn.visibility = View.VISIBLE
                        renderAuditLogs()
                    },
                    onFailure = { err ->
                        txtError.text = err
                        txtError.visibility = View.VISIBLE
                        copyBtn.visibility = View.VISIBLE
                        renderAuditLogs()
                    }
                )
            }

            copyBtn.setOnClickListener {
                val resultText = if (txtResult.visibility == View.VISIBLE) txtResult.text.toString() else txtError.text.toString()
                if (resultText.isNotEmpty()) {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("ABX Local Bridge Result", resultText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Result copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun executeLocalBridgeRequest(
        inputText: String,
        allowedRootsStr: String,
        allowedOpsStr: String,
        maxReqStr: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (currentSessionState !is SessionState.ACTIVE) {
            AuditLog.recordRejection(ReasonCode.SESSION_EXPIRED, "unknown", "Local bridge request rejected: session is inactive")
            onFailure("Blocked: session had ended")
            return
        }

        val sessionId = sessionManager.sessionId ?: "unknown"
        val parsed = parseSharedRequest(inputText)
        if (parsed == null) {
            onFailure("couldn't understand this request")
            return
        }

        val parsedRoots = allowedRootsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val parsedOps = allowedOpsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val parsedMaxReq = maxReqStr.toIntOrNull() ?: 0

        val capability = Capability(
            sessionId = sessionId,
            expiry = System.currentTimeMillis() + sessionManager.getSessionTtl() * 1000L,
            allowedOperations = parsedOps,
            allowedRoots = parsedRoots,
            nonceSeed = "local_bridge_seed",
            maxRequestCount = parsedMaxReq
        )

        val reqObj = JSONObject()
        try {
            reqObj.put("id", 1)
            reqObj.put("jsonrpc", "2.0")
            reqObj.put("method", parsed.operation)
            val paramsObj = JSONObject()
            paramsObj.put("path", parsed.path)
            if (parsed.content.isNotEmpty()) {
                paramsObj.put("content", parsed.content)
            }
            paramsObj.put("encoding", parsed.encoding)
            reqObj.put("params", paramsObj)
        } catch (e: Exception) {
            onFailure("couldn't understand this request")
            return
        }

        try {
            val resultStr = mcpExecutor.execute(reqObj.toString(), capability, currentSessionState)
            val afterEntries = AuditLog.getEntries()
            val isError = resultStr.contains("\"error\"")
            if (isError) {
                val lastEntry = afterEntries.lastOrNull()
                val reasonCodeStr = lastEntry?.optString("reasonCode", "UNKNOWN") ?: "UNKNOWN"
                val plainReason = when (reasonCodeStr) {
                    "SESSION_EXPIRED" -> "Blocked: session had ended"
                    "REPLAY_DETECTED" -> "Blocked: duplicate request detected"
                    "PATH_OUT_OF_BOUNDS" -> "Blocked: access outside of authorized folders"
                    "OP_NOT_ALLOWED" -> "Blocked: operation not allowed"
                    "SAF_REVOKED" -> "Blocked: system permission revoked"
                    "TIER_VIOLATION" -> "Blocked: restricted operation attempted"
                    "REQUEST_COUNT_EXCEEDED" -> "Blocked: request limit exceeded"
                    else -> "Blocked: security policy violation"
                }
                onFailure(plainReason)
            } else {
                onSuccess(resultStr)
            }
        } catch (e: Exception) {
            onFailure("Blocked: security policy violation")
        }
    }

    private fun parseSharedRequest(input: String): ParsedMcpRequest? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        try {
            val json = JSONObject(trimmed)
            var method = json.optString("method", json.optString("operation", json.optString("name", "")))
            var params = json.optJSONObject("params") ?: json.optJSONObject("arguments") ?: json

            if (method == "tools/call" || method == "call_tool") {
                method = params.optString("name", "")
                params = params.optJSONObject("arguments") ?: params
            }

            val path = params.optString("path", "")
            val content = params.optString("content", "")
            val encoding = params.optString("encoding", "text")

            if (method.isNotEmpty() && path.isNotEmpty()) {
                return ParsedMcpRequest(method, path, content, encoding)
            }
        } catch (e: Exception) {
            // Ignore JSON parse failure and fall back to plain text
        }

        val lines = trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        var operation = ""
        var path = ""
        var content = ""
        var encoding = "text"

        for (line in lines) {
            if (line.startsWith("operation:", ignoreCase = true)) {
                operation = line.substring("operation:".length).trim()
            } else if (line.startsWith("method:", ignoreCase = true)) {
                operation = line.substring("method:".length).trim()
            } else if (line.startsWith("path:", ignoreCase = true)) {
                path = line.substring("path:".length).trim()
            } else if (line.startsWith("content:", ignoreCase = true)) {
                content = line.substring("content:".length).trim()
            } else if (line.startsWith("encoding:", ignoreCase = true)) {
                encoding = line.substring("encoding:".length).trim()
            }
        }

        if (operation.isNotEmpty() && path.isNotEmpty()) {
            return ParsedMcpRequest(operation, path, content, encoding)
        }

        val firstLine = lines.firstOrNull() ?: ""
        val colonIndex = firstLine.indexOf(':')
        if (colonIndex > 0) {
            val potentialOp = firstLine.substring(0, colonIndex).trim().lowercase()
            val validOps = listOf(
                "read_file", "read", "write_file", "write", "list_directory", "list",
                "file_exists", "exists", "get_file_metadata", "metadata",
                "get_file_version", "version", "append_file", "append", "delete_file", "delete"
            )
            if (validOps.contains(potentialOp)) {
                var remaining = firstLine.substring(colonIndex + 1).trim()
                var parsedContent = ""
                val contentTag = "content:"
                val contentIndex = remaining.indexOf(contentTag, ignoreCase = true)
                if (contentIndex > 0) {
                    parsedContent = remaining.substring(contentIndex + contentTag.length).trim()
                    remaining = remaining.substring(0, contentIndex).trim()
                }

                if (remaining.isNotEmpty()) {
                    val resolvedOp = when (potentialOp) {
                        "read" -> "read_file"
                        "write" -> "write_file"
                        "list" -> "list_directory"
                        "exists" -> "file_exists"
                        "metadata" -> "get_file_metadata"
                        "version" -> "get_file_version"
                        "append" -> "append_file"
                        "delete" -> "delete_file"
                        else -> potentialOp
                    }
                    return ParsedMcpRequest(resolvedOp, remaining, parsedContent, "text")
                }
            }
        }

        return null
    }

    private fun extractBridgeDisplayResult(rawResponse: String): String {
        return try {
            val obj = JSONObject(rawResponse)
            val result = obj.optJSONObject("result") ?: return rawResponse
            if (result.has("content")) {
                result.optString("content")
            } else {
                result.toString(2)
            }
        } catch (e: Exception) {
            rawResponse
        }
    }

    private fun showDiagnosticsDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_diagnostics, null)
        val switchRemote = dialogView.findViewById<SwitchMaterial>(R.id.switchRemoteReporting)
        val btnExport = dialogView.findViewById<MaterialButton>(R.id.btnExportDiagnosticBundle)
        val btnLogViewer = dialogView.findViewById<MaterialButton>(R.id.btnOpenLogViewerDialog)

        switchRemote.isChecked = CrashReporterManager.isFirebaseEnabled()
        switchRemote.setOnCheckedChangeListener { _, isChecked ->
            CrashReporterManager.updateReportingPreference(requireContext(), isChecked)
        }

        btnExport.setOnClickListener {
            try {
                val bundle = DiagnosticBundle.createBundle(requireContext())
                DiagnosticExporter.shareDiagnosticBundle(requireContext(), bundle)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to generate bundle: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnLogViewer.setOnClickListener {
            startActivity(Intent(requireContext(), LogViewerActivity::class.java))
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showPairingDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_pairing, null)
        val editCode = dialogView.findViewById<EditText>(R.id.editPairingCode)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Enroll") { _, _ ->
                val code = editCode.text.toString().trim()
                gatewayPairedStatus = if (code.isNotEmpty()) {
                    "Paired with Gateway: $code"
                } else {
                    "Paired with Mock Gateway (https://abc-gateway.local/enroll)"
                }
                refreshConnectEnclaveViews()
                Toast.makeText(requireContext(), getString(R.string.dialog_enroll_success), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderAuditLogs() {
        val logsContainer = view?.findViewById<LinearLayout>(R.id.layoutActivityLogsList) ?: return
        val emptyText = view?.findViewById<TextView>(R.id.txtActivityEmpty)
        val txtIntegrity = view?.findViewById<TextView>(R.id.txtActivityIntegrity)
        val switchAdvanced = view?.findViewById<SwitchMaterial>(R.id.switchActivityAdvanced)
        val showAdvanced = switchAdvanced?.isChecked == true

        val logs = AuditLog.getEntries()
        val isChainSecure = AuditLog.verifyIntegrity()

        if (isChainSecure) {
            txtIntegrity?.setText(R.string.audit_integrity_valid)
            txtIntegrity?.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.color_success_container))
            txtIntegrity?.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_success))
        } else {
            txtIntegrity?.setText(R.string.audit_integrity_invalid)
            txtIntegrity?.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.color_error_container))
            txtIntegrity?.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_error))
        }

        logsContainer.removeAllViews()
        if (logs.isEmpty()) {
            emptyText?.visibility = View.VISIBLE
        } else {
            emptyText?.visibility = View.GONE
            logs.reversed().forEach { json ->
                val card = MaterialCardView(requireContext(), null, R.style.Widget_Abx_Card)
                val param = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                param.setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.spacing_sm))
                card.layoutParams = param

                val itemLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.spacing_md),
                        resources.getDimensionPixelSize(R.dimen.spacing_md),
                        resources.getDimensionPixelSize(R.dimen.spacing_md),
                        resources.getDimensionPixelSize(R.dimen.spacing_md)
                    )
                }

                val rawReason = json.optString("reasonCode", "UNKNOWN")
                val details = json.optString("details", "")
                val sessionId = json.optString("sessionId", "N/A")
                val timestamp = json.optLong("timestamp", 0L)

                val translatedReason = when (rawReason) {
                    "SESSION_EXPIRED" -> "Blocked: session had ended"
                    "REPLAY_DETECTED" -> "Blocked: duplicate request detected"
                    "PATH_OUT_OF_BOUNDS" -> "Blocked: access outside of authorized folders"
                    "OP_NOT_ALLOWED" -> "Blocked: operation not allowed"
                    "SAF_REVOKED" -> "Blocked: system permission revoked"
                    "TIER_VIOLATION" -> "Blocked: restricted operation attempted"
                    "REQUEST_COUNT_EXCEEDED" -> "Blocked: request limit exceeded"
                    "SUCCESS" -> "Success: authorized operation completed"
                    "TUNNEL_START" -> "Info: tunnel connection established"
                    "TUNNEL_STOP" -> "Info: tunnel connection closed"
                    "SESSION_APPROVAL" -> "Info: session approved"
                    else -> "Blocked: security policy violation"
                }

                val dateStr = try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    sdf.format(Date(timestamp))
                } catch (e: Exception) {
                    "Unknown time"
                }

                val titleText = TextView(requireContext()).apply {
                    text = translatedReason
                    setTextAppearance(R.style.TextAppearance_Abx_BodyMedium)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.color_error))
                }

                val dateText = TextView(requireContext()).apply {
                    text = dateStr
                    setTextAppearance(R.style.TextAppearance_Abx_LabelSmall)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.color_on_surface_variant))
                }

                val detailsText = TextView(requireContext()).apply {
                    text = "Policy Exception Details: $details"
                    setTextAppearance(R.style.TextAppearance_Abx_BodySmall)
                    setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing_xs), 0, 0)
                }

                val sessionText = TextView(requireContext()).apply {
                    text = "Session ID: $sessionId"
                    setTextAppearance(R.style.TextAppearance_Abx_Monospace)
                    setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing_xs), 0, 0)
                }

                itemLayout.addView(titleText)
                itemLayout.addView(dateText)
                itemLayout.addView(detailsText)
                itemLayout.addView(sessionText)

                if (showAdvanced) {
                    val jsonText = TextView(requireContext()).apply {
                        text = json.toString(2)
                        setTextAppearance(R.style.TextAppearance_Abx_Monospace)
                        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.gray_85))
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.success_green_40))
                        setPadding(
                            resources.getDimensionPixelSize(R.dimen.spacing_sm),
                            resources.getDimensionPixelSize(R.dimen.spacing_sm),
                            resources.getDimensionPixelSize(R.dimen.spacing_sm),
                            resources.getDimensionPixelSize(R.dimen.spacing_sm)
                        )
                    }
                    itemLayout.addView(jsonText)
                }

                card.addView(itemLayout)
                logsContainer.addView(card)
            }
        }
    }

    private fun getTruncatedFingerprint(fp: String): String {
        if (fp.length < 16) return fp
        val firstPart = fp.take(4)
        val secondPart = fp.substring(4, 8)
        val lastSecondPart = fp.substring(fp.length - 8, fp.length - 4)
        val lastPart = fp.takeLast(4)
        return "$firstPart • $secondPart • ... • $lastSecondPart • $lastPart"
    }

    private fun generateQrCodeBitmap(content: String, size: Int): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun generateMockCapabilityJson(): String {
        val isActive = currentSessionState is SessionState.ACTIVE
        val mockToken = JSONObject().apply {
            put("sessionId", if (isActive) "sess_active_99" else "sess_inactive_00")
            put("expiry", System.currentTimeMillis() + if (isActive) ttlRemaining * 1000L else 0L)
            put("allowedOperations", listOf("read_file", "write_file", "list_directory"))
            put("allowedRoots", listOf("/storage/emulated/0/Download", "/storage/emulated/0/Documents"))
            put("nonceSeed", "seed_abc_123_xyz")
            put("issuedTime", System.currentTimeMillis())
            put("maxRequestCount", 0)
            put("fingerprint", formattedFingerprint)
        }
        return mockToken.toString(2)
    }

    data class ParsedMcpRequest(
        val operation: String,
        val path: String,
        val content: String = "",
        val encoding: String = "text"
    )
}
