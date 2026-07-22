package com.inscopelabs.abx.server

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

    enum class Workspace {
        WORKSPACE,
        SERVER,
        TOOLBOX
    }

    private var currentWorkspace = Workspace.SERVER
    private var previousWorkspace = Workspace.SERVER

    private var sharedTextState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BootRoute.redirectIfNeeded(this)) return

        setContentView(R.layout.root_canvas)

        handleIntent(intent)

        // Only a single fragment is ever shown in mainContentContainer.
        // Loading is first; the default Server view is shown after startup sequence.
        showFragment(LoadingFragment())

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
     * default (visible) main view: Server is shown by default.
     */
    private fun onStartupComplete() {
        showWorkspace(Workspace.SERVER)
        previousWorkspace = Workspace.SERVER
    }

    fun switchTopLevelWorkspace(target: Workspace) {
        showWorkspace(target)
    }

    override fun openSecureTab(tab: SecureTab) {
        if (currentWorkspace == Workspace.SERVER) {
            val fragment = supportFragmentManager.findFragmentById(R.id.mainContentContainer) as? ServerFragment
            fragment?.selectTab(tab)
        } else {
            showWorkspace(Workspace.SERVER, tab)
        }
    }

    private fun showWorkspace(workspace: Workspace, secureTab: SecureTab = SecureTab.CONNECT) {
        currentWorkspace = workspace
        val fragment = when (workspace) {
            Workspace.WORKSPACE -> WorkspaceFragment()
            Workspace.SERVER -> ServerFragment.newInstance(secureTab)
            Workspace.TOOLBOX -> ToolboxFragment()
        }
        showFragment(fragment)
    }

    fun openToolbox() {
        if (currentWorkspace != Workspace.TOOLBOX) {
            previousWorkspace = currentWorkspace
            showWorkspace(Workspace.TOOLBOX)
        }
    }

    override fun returnFromToolbox() {
        when (previousWorkspace) {
            Workspace.WORKSPACE -> showWorkspace(Workspace.WORKSPACE)
            Workspace.SERVER -> showWorkspace(Workspace.SERVER)
            else -> showWorkspace(Workspace.SERVER)
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
