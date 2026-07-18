package com.inscopelabs.abx.server.bridge

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class JsBridgeManager(
    private val webView: WebView,
    private val actionHandler: JsActionHandler,
    context: Context,
    // Origins are validated by Chromium as bare scheme+host+port with no
    // path — "file:///android_asset/" is NOT valid here (confirmed crash:
    // IllegalArgumentException "allowedOriginRules ... is invalid" thrown
    // synchronously inside addWebMessageListener). Tools are served through
    // WebViewAssetLoader under this virtual https origin instead, which is
    // Google's documented pattern for bridging local asset HTML with
    // origin-based WebView security APIs.
    private val allowedOrigins: Set<String> = setOf(ASSET_LOADER_ORIGIN)
) {

    companion object {
        const val ASSET_LOADER_DOMAIN = "appassets.androidplatform.net"
        const val ASSET_LOADER_ORIGIN = "https://$ASSET_LOADER_DOMAIN"
    }

    private val assetLoader = WebViewAssetLoader.Builder()
        .setDomain(ASSET_LOADER_DOMAIN)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

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
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

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

    /** Load a tool bundled under assets/tools/<assetDir>/index.html via the asset loader origin. */
    fun loadTool(assetDir: String) {
        webView.loadUrl("$ASSET_LOADER_ORIGIN/assets/tools/$assetDir/index.html")
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
