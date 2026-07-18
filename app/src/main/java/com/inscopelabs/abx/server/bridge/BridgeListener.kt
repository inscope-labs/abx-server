package com.inscopelabs.abx.server.bridge

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import org.json.JSONObject

class BridgeListener(
    private val actionHandler: JsActionHandler,
    private val allowedOrigins: Set<String>
) : WebViewCompat.WebMessageListener {

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        // 1. SECURITY: Validate origin against the tool's declared allow-list.
        val origin = sourceOrigin.toString()
        if (!allowedOrigins.contains(origin)) {
            replyProxy.postMessage(errorResponse("Invalid origin: $origin"))
            return
        }

        // 2. SECURITY: block iframe-sourced messages.
        if (!isMainFrame) {
            replyProxy.postMessage(errorResponse("Messages from iframes blocked"))
            return
        }

        // 3. Parse the message.
        val requestJson = try {
            JSONObject(message.data ?: "{}")
        } catch (e: Exception) {
            replyProxy.postMessage(errorResponse("Invalid JSON"))
            return
        }

        val action = requestJson.optString("action", "")
        val requestId = requestJson.optString("requestId", "")
        val payload = requestJson.optJSONObject("payload") ?: JSONObject()

        if (action.isEmpty()) {
            replyProxy.postMessage(errorResponse("Missing 'action' field"))
            return
        }

        // 4. Route to the injected handler (AbxToolActionHandler in production).
        try {
            val result = actionHandler.handle(action, payload)
            replyProxy.postMessage(
                JSONObject().apply {
                    put("requestId", requestId)
                    put("success", true)
                    put("data", result)
                }.toString()
            )
        } catch (e: Exception) {
            replyProxy.postMessage(
                JSONObject().apply {
                    put("requestId", requestId)
                    put("success", false)
                    put("error", e.message ?: "Unknown error")
                }.toString()
            )
        }
    }

    private fun errorResponse(msg: String): String {
        return JSONObject().apply {
            put("success", false)
            put("error", msg)
        }.toString()
    }
}
