package com.inscopelabs.abx.server.bridge

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class JsBridgeManager(
    private val webView: WebView,
    private val actionHandler: JsActionHandler,
    private val allowedOrigins: Set<String> = setOf("file:///android_asset/")
) {

    init {
        configureWebView()
        registerBridge()
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Bridge is ready after page load.
            }
        }
    }

    private fun registerBridge() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                "nativeBridge",
                allowedOrigins,
                BridgeListener(actionHandler, allowedOrigins)
            )
        } else {
            throw UnsupportedOperationException(
                "WebMessageListener not supported on this device/WebView version — " +
                "Toolbox tools require a WebView release supporting addWebMessageListener."
            )
        }
    }

    /** Push a native-originated event into the tool's JS (e.g. session ended). */
    fun sendToJs(functionName: String, jsonData: String) {
        val sanitized = JSONObject.quote(jsonData)
        webView.post {
            webView.evaluateJavascript("window.$functionName($sanitized);", null)
        }
    }

    fun destroy() {
        webView.apply {
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            onPause()
            removeAllViews()
            destroy()
        }
    }
}
