package com.inscopelabs.abx.server

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.inscopelabs.abx.server.boot.BootRoute
import com.inscopelabs.abx.server.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BootRoute.redirectIfNeeded(this)) return
        enableEdgeToEdge()

        val app = application as HelloApplication
        val keyStoreManager = app.keyStoreManager

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PlaceholderScreen(
                        keyStoreManager = keyStoreManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
