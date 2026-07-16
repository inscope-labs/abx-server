# Process Report: Contextual Runtime Request for POST_NOTIFICATIONS Permission

- **Timestamp:** 2026-07-16T20:37:13Z
- **Task Slug:** post-notifications-contextual-fix

---

## 1. What was asked
The goal was to implement `POST_NOTIFICATIONS` runtime permission request in Jetpack Compose, specifically inside `EnrollmentScreen.kt`.
- **Architectural Context**: The original `abx-mcp` application requests the permission eagerly at startup (`MainActivity.onCreate()`). This is a cold-launch permission request pattern, which violates modern Android best practices (requesting permissions contextually right before they are needed) and frequently leads to flags by Google Play Console reviewers.
- **Goal**: To pass Google Play Console review for `abx-server`, implement a contextual permission request pattern. Request the notification permission only when the user actually interacts with the "Start Session" button, immediately before `TunnelService.start()` is triggered.
- **Constraints**:
  - Do NOT modify `MainActivity.kt` (ensure no cold-launch or eager permission prompt is present).
  - Do NOT modify `AndroidManifest.xml` (the permission is already correctly declared).
  - Modify ONLY `EnrollmentScreen.kt`.
  - Follow `AGENTS.md` drift protection guidelines before performing any modifications.

---

## 2. Drift-Protection Audit
- **Action**: Checked local `EnrollmentScreen.kt` content against the live file content at `https://raw.githubusercontent.com/inscope-labs/abx-server/main/app/src/main/java/com/inscopelabs/abx/server/EnrollmentScreen.kt`.
- **Finding**: Verified 100% parity between local working copy and GitHub remote main. Zero drift detected.

---

## 3. Changes Applied

### `app/src/main/java/com/inscopelabs/abx/server/EnrollmentScreen.kt`
1. **Added imports**:
   - `androidx.activity.compose.rememberLauncherForActivityResult`
   - `androidx.activity.result.contract.ActivityResultContracts`
   - `androidx.core.content.ContextCompat`
   - `android.Manifest`
   - `android.content.pm.PackageManager`
2. **Declared Launcher**:
   Defined a Compose activity result launcher inside the `EnrollmentScreen` scope:
   ```kotlin
   val notificationPermissionLauncher = rememberLauncherForActivityResult(
       ActivityResultContracts.RequestPermission()
   ) { /* No-op handled gracefully by TunnelService if missing */ }
   ```
3. **Modified `onStartSession` block**:
   Updated the try block of the start session callback to perform a Tiramisu (API 33)+ check and verify if the notification permission is already granted. If not, trigger the system permission dialog contextually, immediately before starting the session and booting the service:
   ```kotlin
   onStartSession = {
       try {
           if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
               if (ContextCompat.checkSelfPermission(
                       context,
                       Manifest.permission.POST_NOTIFICATIONS
                   ) != PackageManager.PERMISSION_GRANTED
               ) {
                   notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
               }
           }
           sessionManager.startSession(UserGesture.LocalButtonPress)
           com.inscopelabs.abx.server.core.tunnel.TunnelService.start(context)
       } catch (e: Exception) {
           coroutineScope.launch {
               snackbarHostState.showSnackbar("Cannot start session: ${e.message}")
           }
       }
   }
   ```

---

## 4. Intentional Deviation Notice
This modification is an **intentional architectural deviation** from the reference `abx-mcp` application. While `abx-mcp` prompts the user for notification permissions on application startup (cold launch), `abx-server` implements a contextual runtime permission flow. 

This deviation is introduced at the user's explicit request to ensure full compliance with Google Play Developer policies regarding user experience and permission minimization. Placing the request immediately before posting a notification improves user trust, enhances UX, and avoids rejection in the live Play Console review process.

---

## 5. Verification
- **Compilation**: Ran `compile_applet` successfully to verify code syntax and imports.
- **Zero-touch Check**: Confirmed that `MainActivity.kt` and `AndroidManifest.xml` remain unmodified.
