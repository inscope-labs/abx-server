package com.inscopelabs.abx.server

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {
    companion object {
        // Placeholder gate only. TODO: replace with the real deferred
        // startup sequence (KeyStoreManager / AuditLog init, etc.) once
        // that is wired back in — see Phase 1.4/1.5 discussion.
        private const val PLACEHOLDER_STARTUP_DELAY_MS = 2000L
    }

    private var sharedTextState by mutableStateOf<String?>(null)
    private val startupGateHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.root_canvas)

        // Only a single fragment is ever shown in mainContentContainer.
        // Loading is first; the toggle row stays hidden until the gate
        // completes and the default Files view is shown.
        showFragment(LoadingFragment())
        findViewById<View>(R.id.chatFilesToggleRow).visibility = View.GONE

        startupGateHandler.postDelayed({ onStartupComplete() }, PLACEHOLDER_STARTUP_DELAY_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        startupGateHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Called exactly once, when the startup process finishes. Gates the
     * default (visible) main view: Files is not shown until this runs.
     */
    private fun onStartupComplete() {
        showFragment(FilesFragment())

        val toggleRow = findViewById<View>(R.id.chatFilesToggleRow)
        toggleRow.visibility = View.VISIBLE

        val toggle = findViewById<SwitchCompat>(R.id.chatFilesToggle)
        toggle.isChecked = false // unchecked = Files (the default just shown)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            showFragment(if (isChecked) ChatFragment() else FilesFragment())
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
