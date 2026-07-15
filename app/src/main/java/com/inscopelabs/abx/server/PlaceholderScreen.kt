package com.inscopelabs.abx.server

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager

@Composable
fun PlaceholderScreen(
    keyStoreManager: KeyStoreManager?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (keyStoreManager != null) {
                "KeyStoreManager attached (backend: ${if (keyStoreManager.isAndroidKeyStore) "AndroidKeyStore" else "JVM sandbox"})"
            } else {
                "KeyStoreManager unavailable"
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Compose rendering pipeline: stage 11 placeholder. Real EnrollmentScreen arrives in stage 13.1.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
