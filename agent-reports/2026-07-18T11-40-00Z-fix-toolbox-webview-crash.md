# Task Report: Fix Toolbox Crash (Invalid WebView Origin Rule on Tool Selection)

- **Timestamp**: 2026-07-18T11:40:00Z
- **Task Slug**: fix-toolbox-webview-crash
- **Lead Architect**: ABX Platform Lead Android Software Architect

---

## 1. Objective & What Was Asked
We were tasked with resolving a critical crash affecting the Toolbox section of the ABX Server application.
When any tool in the Toolbox was selected, the app crashed with an `IllegalArgumentException` inside the Chromium WebView subsystem during the registration of the JavaScript-to-Native bridge (`WebViewCompat.addWebMessageListener`). 
* **Root Cause**: Chromium validates `allowedOriginRules` as strict scheme+host+port sequences without path components. The previous implementation passed `"file:///android_asset/"` as an allowed origin rule, which has a path component and is therefore rejected synchronously by Chromium.

---

## 2. Changes Applied

We implemented the Google-recommended approach of hosting local asset HTML under a secure virtual domain using `WebViewAssetLoader` with domain `appassets.androidplatform.net`, which allows bridging local asset HTML with origin-based security APIs cleanly.

### A. Modified `app/src/main/java/com/inscopelabs/abx/server/bridge/JsBridgeManager.kt`
- Added support for `WebViewAssetLoader` configured for the domain `appassets.androidplatform.net`.
- Configured a `WebViewClient` that intercepts requests via `shouldInterceptRequest` and routes virtual `/assets/` path requests to local assets via `WebViewAssetLoader.AssetsPathHandler(context)`.
- Updated the constructor of `JsBridgeManager` to require a `Context` parameter.
- Set the default `allowedOrigins` list to `https://appassets.androidplatform.net` (which contains no paths or trailing slashes, complying perfectly with Chromium's validation).
- Added `loadTool(assetDir: String)` to resolve local HTML assets cleanly over the virtual `https://appassets.androidplatform.net/assets/tools/<assetDir>/index.html` origin instead of a direct raw `file://` scheme load.

### B. Modified `app/src/main/java/com/inscopelabs/abx/server/toolbox/ToolRunnerScreen.kt`
- Updated the Compose `AndroidView` factory to pass the Composable's host `Context` into `JsBridgeManager`.
- Replaced the direct `webView.loadUrl("file:///android_asset/...")` call with `bridgeManager.loadTool(tool.assetDir)` to leverage the virtual asset loader host.

---

## 3. Commands Executed & Results
* **Compilation**: `compile_applet` was executed.
* **Status**: **Build Succeeded** — both classes compiled cleanly, confirming that layout resources, R imports, and parameter types match and resolve with zero warning or compilation failure.

---

## 4. Verification & Assumptions
* **Drift Protection Check**: Performed a live file drift check on both files prior to editing. The local files perfectly matched the `main` branch state on GitHub.
* **WebView Integration Safety**: Checked that other bridge classes such as `BridgeListener`, `AbxToolActionHandler`, and `ToolCatalog` were unaffected and required zero modifications.
