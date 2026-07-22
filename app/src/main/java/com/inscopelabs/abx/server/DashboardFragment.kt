package com.inscopelabs.abx.server

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.KeyPair

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private companion object {
        private const val ALIAS = "abx_mcp_device_key"
    }

    private var secureNav: SecureNavigation? = null

    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var sessionManager: SessionManager

    private var currentSessionState: SessionState = SessionState.INACTIVE
    private var ttlRemaining = 0
    private var timerJob: Job? = null

    private var isHardwareBacked = false
    private var isStrongBoxBacked = false
    private var fingerprint: String = ""

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

        initDependencies()
        setupGestureDetector(view)
        bindViews(view)
        observeSessionState()
        loadKeyMaterial()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun initDependencies() {
        keyStoreManager = KeyStoreManager(requireContext().applicationContext)
        sessionManager = SessionManagerProvider.get(requireContext().applicationContext)
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

    private fun bindViews(view: View) {
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
                    startTimer()
                } else {
                    timerJob?.cancel()
                    ttlRemaining = 0
                }
                refreshUI()
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
                refreshUI()
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
            val rawFp = FingerprintUtils.getFingerprint(kp.public)
            fingerprint = rawFp
            updateHardwareStatus(kp)
            refreshUI()
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

    private fun refreshUI() {
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
}
