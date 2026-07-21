package com.inscopelabs.abx.server

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.appbar.MaterialToolbar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import com.inscopelabs.abx.server.boot.BootGuard
import com.inscopelabs.abx.server.boot.BootRoute
import com.inscopelabs.abx.server.compliance.AboutBottomSheet
import com.inscopelabs.abx.server.compliance.DeleteDataBottomSheet
import com.inscopelabs.abx.server.compliance.PrivacyPolicyBottomSheet
import com.inscopelabs.abx.server.core.audit.AuditLog
import com.inscopelabs.abx.server.core.diagnostics.AnrWatchdog
import com.inscopelabs.abx.server.core.diagnostics.Logger
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager

interface ToolboxNavigation {
    fun returnFromToolbox()
}

class MainActivity : AppCompatActivity(), ToolboxNavigation {
    companion object {
        // Process-lifetime guard: prevents re-running the startup sequence
        // if MainActivity is recreated (e.g. config change) within the same
        // process.
        @Volatile
        private var startupSequenceRan = false
    }

    private enum class Workspace {
        FILES,
        CHAT,
        TOOLBOX
    }

    private var currentWorkspace = Workspace.FILES
    private var previousWorkspace = Workspace.FILES

    private var sharedTextState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BootRoute.redirectIfNeeded(this)) return

        setContentView(R.layout.root_canvas)

        setupRootToolbar()

        // Only a single fragment is ever shown in mainContentContainer.
        // Loading is first; the toggle row stays hidden until the gate
        // completes and the default Files view is shown.
        showFragment(LoadingFragment())
        findViewById<View>(R.id.chatFilesToggleRow).visibility = View.GONE

        runStartupSequence()
    }

    /**
     * Wires the permanent root toolbar (rootToolbar in root_canvas.xml).
     * Hamburger and overflow items are stubbed — no drawer or destination
     * screens exist yet to route to.
     */
    private fun setupRootToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.rootToolbar)
        toolbar.setNavigationOnClickListener {
            // TODO: wire to drawer / primary nav destination picker once available
        }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_about -> {
                    AboutBottomSheet().show(supportFragmentManager, "AboutBottomSheet")
                    true
                }
                R.id.menu_privacy_policy -> {
                    PrivacyPolicyBottomSheet().show(supportFragmentManager, "PrivacyPolicyBottomSheet")
                    true
                }
                R.id.menu_delete_data -> {
                    DeleteDataBottomSheet().show(supportFragmentManager, "DeleteDataBottomSheet")
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Restored loading components: Logger, AnrWatchdog, KeyStoreManager,
     * AuditLog. Runs synchronously, in dependency order: Logger before
     * anything that logs; AnrWatchdog after Logger; KeyStoreManager before
     * AuditLog, since AuditLog.initialize() requires it. Gates
     * onStartupComplete() either way (success or failure).
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
     * default (visible) main view: Files is not shown until this runs.
     */
    private fun onStartupComplete() {
        showWorkspace(Workspace.FILES)
        previousWorkspace = Workspace.FILES

        val toggleRow = findViewById<View>(R.id.chatFilesToggleRow)
        toggleRow.visibility = View.VISIBLE

        val toggle = findViewById<SwitchCompat>(R.id.chatFilesToggle)
        toggle.isChecked = false // unchecked = Files (the default just shown)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            val target = if (isChecked) Workspace.CHAT else Workspace.FILES
            if (currentWorkspace == Workspace.TOOLBOX) {
                previousWorkspace = target
            } else {
                showWorkspace(target)
            }
        }
    }

    private fun showWorkspace(workspace: Workspace) {
        currentWorkspace = workspace
        val fragment = when (workspace) {
            Workspace.FILES -> FilesFragment()
            Workspace.CHAT -> ChatFragment()
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
            Workspace.FILES -> showWorkspace(Workspace.FILES)
            Workspace.CHAT -> showWorkspace(Workspace.CHAT)
            else -> showWorkspace(Workspace.FILES)
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

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                sharedTextState = sharedText
            }
        }
    }
}

