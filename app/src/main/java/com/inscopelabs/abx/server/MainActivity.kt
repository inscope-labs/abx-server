package com.inscopelabs.abx.server

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
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
}
