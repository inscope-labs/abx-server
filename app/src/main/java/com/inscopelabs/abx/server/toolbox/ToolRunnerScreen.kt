package com.inscopelabs.abx.server.toolbox

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.inscopelabs.abx.server.bridge.JsBridgeManager
import com.inscopelabs.abx.server.core.mcp.McpExecutor
import com.inscopelabs.abx.server.core.session.SessionManager

/**
 * Hosts a single Toolbox tool in a sandboxed WebView. Origin is locked to
 * file:///android_asset/ — tools are bundled assets, not remote pages, so
 * there is no network-loaded content in scope here.
 */
@Composable
fun ToolRunnerScreen(
    tool: ToolDefinition,
    sessionManager: SessionManager,
    mcpExecutor: McpExecutor,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("tool_runner_back")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to Toolbox")
                }
                Text(text = tool.title, style = MaterialTheme.typography.titleSmall)
            }
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("tool_runner_webview"),
            factory = { context ->
                WebView(context).apply {
                    val handler = AbxToolActionHandler(
                        tool = tool,
                        sessionManager = sessionManager,
                        mcpExecutor = mcpExecutor
                    )
                    val bridgeManager = JsBridgeManager(
                        webView = this,
                        actionHandler = handler,
                        allowedOrigins = setOf("file:///android_asset/")
                    )
                    tag = bridgeManager
                    loadUrl("file:///android_asset/tools/${tool.assetDir}/index.html")
                }
            }
        )
    }

    DisposableEffect(tool.id) {
        onDispose { /* WebView is torn down with its host composition */ }
    }
}
