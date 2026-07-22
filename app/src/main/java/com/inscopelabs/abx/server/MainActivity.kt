package com.inscopelabs.abx.server

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import com.inscopelabs.abx.server.boot.BootGuard
import com.inscopelabs.abx.server.boot.BootRoute
import com.inscopelabs.abx.server.core.audit.AuditLog
import com.inscopelabs.abx.server.core.diagnostics.AnrWatchdog
import com.inscopelabs.abx.server.core.diagnostics.Logger
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager
import com.inscopelabs.abx.server.workspace.SecureNavigation
import com.inscopelabs.abx.server.workspace.SecureTab

interface ToolboxNavigation {
    fun returnFromToolbox()
}

class MainActivity : AppCompatActivity(), ToolboxNavigation, SecureNavigation {
    companion object {
        // Process-lifetime guard: prevents re-running the startup sequence
        // if MainActivity is recreated (e.g. config change) within the same
        // process.
        @Volatile
        private var startupSequenceRan = false
    }

    private enum class Workspace {
        CHAT,
        DASHBOARD,
        SECURE,
        TOOLBOX
    }

    private var currentWorkspace = Workspace.SECURE
    private var previousWorkspace = Workspace.SECURE

    private var sharedTextState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BootRoute.redirectIfNeeded(this)) return

        setContentView(R.layout.root_canvas)

        handleIntent(intent)

        // Only a single fragment is ever shown in mainContentContainer.
        // Loading is first; the switcher row stays hidden until the gate
        // completes and the default Secure view is shown.
        showFragment(LoadingFragment())
        findViewById<View>(R.id.workspaceSwitcherRow).visibility = View.GONE

        runStartupSequence()
    }

    /**
     * Restored loading components: Logger, AnrWatchdog, KeyStoreManager,
     * AuditLog. Synchronous startup sequence.
     */
    private fun runStartupSequence() {
        if (startupSequenceRan) {
            onStartupComplete()
            return
        }

        try {
            BootGuard.stageStart("Logger")
            Logger.initialize(applicationContext)
            BootGuard.stageSuccess("Logger")

            BootGuard.stageStart("AnrWatchdog")
            AnrWatchdog().start()
            BootGuard.stageSuccess("AnrWatchdog")

            BootGuard.stageStart("KeyStoreManager")
            val km = KeyStoreManager(applicationContext)
            (application as MainApplication).keyStoreManager = km
            BootGuard.stageSuccess("KeyStoreManager")

            BootGuard.stageStart("AuditLog")
            AuditLog.initialize(applicationContext, km)
            BootGuard.stageSuccess("AuditLog")

            startupSequenceRan = true
        } catch (t: Throwable) {
            BootGuard.recordFailure(applicationContext, "MainActivity.runStartupSequence", t)
        }

        onStartupComplete()
    }

    /**
     * Called exactly once, when the startup process finishes. Gates the
     * default (visible) main view: Secure is shown by default.
     */
    private fun onStartupComplete() {
        showWorkspace(Workspace.SECURE)
        previousWorkspace = Workspace.SECURE

        val switcherRow = findViewById<View>(R.id.workspaceSwitcherRow)
        switcherRow.visibility = View.VISIBLE

        findViewById<View>(R.id.chatWorkspaceButton).setOnClickListener {
            if (currentWorkspace == Workspace.TOOLBOX) {
                previousWorkspace = Workspace.CHAT
            } else {
                showWorkspace(Workspace.CHAT)
            }
        }

        findViewById<View>(R.id.dashboardWorkspaceButton).setOnClickListener {
            if (currentWorkspace == Workspace.TOOLBOX) {
                previousWorkspace = Workspace.DASHBOARD
            } else {
                showWorkspace(Workspace.DASHBOARD)
            }
        }

        findViewById<View>(R.id.secureWorkspaceButton).setOnClickListener {
            if (currentWorkspace == Workspace.TOOLBOX) {
                previousWorkspace = Workspace.SECURE
            } else {
                showWorkspace(Workspace.SECURE)
            }
        }
    }

    override fun openSecureTab(tab: SecureTab) {
        if (currentWorkspace == Workspace.SECURE) {
            val fragment = supportFragmentManager.findFragmentById(R.id.mainContentContainer) as? SecureFragment
            fragment?.selectTab(tab)
        } else {
            showWorkspace(Workspace.SECURE, tab)
        }
    }

    private fun showWorkspace(workspace: Workspace, secureTab: SecureTab = SecureTab.CONNECT) {
        currentWorkspace = workspace
        val fragment = when (workspace) {
            Workspace.CHAT -> ChatFragment()
            Workspace.DASHBOARD -> DashboardFragment()
            Workspace.SECURE -> SecureFragment.newInstance(secureTab)
            Workspace.TOOLBOX -> ToolboxFragment()
        }
        showFragment(fragment)
        updateWorkspaceButtonsUI(workspace)
    }

    private fun updateWorkspaceButtonsUI(active: Workspace) {
        val chatContainer = findViewById<FrameLayout>(R.id.chatIconContainer) ?: return
        val chatIcon = findViewById<ImageView>(R.id.chatIcon) ?: return
        val chatLabel = findViewById<TextView>(R.id.chatLabel) ?: return

        val dashContainer = findViewById<FrameLayout>(R.id.dashboardIconContainer) ?: return
        val dashIcon = findViewById<ImageView>(R.id.dashboardIcon) ?: return
        val dashLabel = findViewById<TextView>(R.id.dashboardLabel) ?: return

        val secureContainer = findViewById<FrameLayout>(R.id.secureIconContainer) ?: return
        val secureIcon = findViewById<ImageView>(R.id.secureIcon) ?: return
        val secureLabel = findViewById<TextView>(R.id.secureLabel) ?: return

        val accentBg = R.drawable.bg_icon_container_accent
        val neutralBg = R.drawable.bg_icon_container_neutral
        val activeColor = ContextCompat.getColor(this, R.color.color_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.color_on_surface_variant)

        // Chat
        val isChat = active == Workspace.CHAT
        chatContainer.setBackgroundResource(if (isChat) accentBg else neutralBg)
        chatIcon.setColorFilter(if (isChat) activeColor else inactiveColor)
        chatLabel.setTextColor(if (isChat) activeColor else inactiveColor)

        // Dashboard
        val isDash = active == Workspace.DASHBOARD
        dashContainer.setBackgroundResource(if (isDash) accentBg else neutralBg)
        dashIcon.setColorFilter(if (isDash) activeColor else inactiveColor)
        dashLabel.setTextColor(if (isDash) activeColor else inactiveColor)

        // Secure
        val isSecure = active == Workspace.SECURE
        secureContainer.setBackgroundResource(if (isSecure) accentBg else neutralBg)
        secureIcon.setColorFilter(if (isSecure) activeColor else inactiveColor)
        secureLabel.setTextColor(if (isSecure) activeColor else inactiveColor)
    }

    fun openToolbox() {
        if (currentWorkspace != Workspace.TOOLBOX) {
            previousWorkspace = currentWorkspace
            showWorkspace(Workspace.TOOLBOX)
        }
    }

    override fun returnFromToolbox() {
        when (previousWorkspace) {
            Workspace.CHAT -> showWorkspace(Workspace.CHAT)
            Workspace.DASHBOARD -> showWorkspace(Workspace.DASHBOARD)
            Workspace.SECURE -> showWorkspace(Workspace.SECURE)
            else -> showWorkspace(Workspace.SECURE)
        }
    }

    /** Replaces mainContentContainer's content — never more than one fragment shown. */
    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContentContainer, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    fun consumeSharedText(): String? {
        val text = sharedTextState
        sharedTextState = null
        return text
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                sharedTextState = sharedText
            }
        }
    }
}
