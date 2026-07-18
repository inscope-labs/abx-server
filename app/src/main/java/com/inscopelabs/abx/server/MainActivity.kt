package com.inscopelabs.abx.server

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager
import com.inscopelabs.abx.server.ui.theme.MyApplicationTheme
import com.inscopelabs.abx.server.boot.BootRoute
import com.inscopelabs.abx.server.compliance.AboutBottomSheet
import com.inscopelabs.abx.server.compliance.PrivacyPolicyBottomSheet

class MainActivity : AppCompatActivity() {
    private var sharedTextState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BootRoute.redirectIfNeeded(this)) return
        enableEdgeToEdge()

        val app = application as HelloApplication
        val keyStoreManager = app.keyStoreManager ?: run {
            val exc = IllegalStateException("MainActivity.onCreate — keyStoreManager unexpectedly null")
            com.inscopelabs.abx.server.boot.BootGuard.recordFailure(
                applicationContext,
                "MainActivity.onCreate — keyStoreManager unexpectedly null",
                exc
            )
            if (com.inscopelabs.abx.server.boot.BootRoute.redirectIfNeeded(this)) return
            KeyStoreManager(applicationContext)
        }

        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EnrollmentScreen(
                        keyStoreManager = keyStoreManager,
                        sharedText = sharedTextState,
                        onClearSharedText = { sharedTextState = null },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_utilities -> {
                throw RuntimeException("Test Uncaught Exception for Diagnostics Verification")
            }
            R.id.menu_about -> {
                AboutBottomSheet().show(supportFragmentManager, "AboutBottomSheet")
                true
            }
            R.id.menu_privacy_policy -> {
                PrivacyPolicyBottomSheet().show(supportFragmentManager, "PrivacyPolicyBottomSheet")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
