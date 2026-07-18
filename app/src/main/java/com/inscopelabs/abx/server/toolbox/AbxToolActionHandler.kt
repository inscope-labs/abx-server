package com.inscopelabs.abx.server.toolbox

import com.inscopelabs.abx.server.bridge.JsActionHandler
import com.inscopelabs.abx.server.bridge.safeGetString
import com.inscopelabs.abx.server.core.mcp.McpExecutor
import com.inscopelabs.abx.server.core.policy.Capability
import com.inscopelabs.abx.server.core.session.SessionManager
import com.inscopelabs.abx.server.core.session.SessionState
import org.json.JSONObject

/**
 * Bridges a Toolbox tool's JavaScript into the app's real MCP execution
 * pipeline — the same McpExecutor.execute(...) path used by the Local
 * Bridge dialog and the external WebSocket relay. This handler does not
 * grant broad access itself: it mints a Capability scoped to exactly the
 * operations/roots the ToolDefinition declared, and PolicyEngine (inside
 * McpExecutor) still authorizes every call against that scope. A tool
 * asking for an operation or path outside its declared scope is rejected
 * by the existing policy layer, not by this class.
 *
 * Supported JS-side actions:
 *  - "mcp_call"      { method, params }        -> delegates to McpExecutor
 *  - "session_status" {}                       -> read-only session info
 */
class AbxToolActionHandler(
    private val tool: ToolDefinition,
    private val sessionManager: SessionManager,
    private val mcpExecutor: McpExecutor
) : JsActionHandler {

    override fun handle(action: String, payload: JSONObject): Any? {
        return when (action) {
            "mcp_call" -> handleMcpCall(payload)
            "session_status" -> handleSessionStatus()
            else -> throw IllegalArgumentException("Unsupported action: $action")
        }
    }

    private fun handleMcpCall(payload: JSONObject): JSONObject {
        val currentState = sessionManager.getState()
        if (currentState !is SessionState.ACTIVE) {
            throw IllegalStateException("Session is not active — start a session before using this tool")
        }

        val method = payload.safeGetString("method")
        if (method.isEmpty()) {
            throw IllegalArgumentException("Missing 'method'")
        }

        // Tool-level allow-list check, ahead of the PolicyEngine check inside
        // McpExecutor — fails fast with a clearer message for the tool author.
        if (tool.allowedOperations.isNotEmpty() && method !in tool.allowedOperations) {
            throw SecurityException(
                "Tool '${tool.id}' is not permitted to call '$method'. " +
                "Allowed: ${tool.allowedOperations.joinToString()}"
            )
        }

        val sessionId = sessionManager.sessionId ?: "unknown"
        val capability = Capability(
            sessionId = sessionId,
            expiry = System.currentTimeMillis() + sessionManager.getSessionTtl() * 1000L,
            allowedOperations = tool.allowedOperations,
            allowedRoots = tool.allowedRoots,
            nonceSeed = "toolbox_${tool.id}",
            maxRequestCount = 0
        )

        val params = payload.optJSONObject("params") ?: JSONObject()
        val reqObj = JSONObject().apply {
            put("id", 1)
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }

        val resultStr = mcpExecutor.execute(reqObj.toString(), capability, currentState)
        return JSONObject(resultStr)
    }

    private fun handleSessionStatus(): JSONObject {
        val state = sessionManager.getState()
        return JSONObject().apply {
            put("sessionId", sessionManager.sessionId ?: JSONObject.NULL)
            put("state", state::class.simpleName)
            put("ttlSeconds", sessionManager.getSessionTtl())
            put("toolId", tool.id)
        }
    }
}
