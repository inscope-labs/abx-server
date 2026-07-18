# Task Report: Replace Default Crash Handler Chaining with Branded CrashActivity

- **Timestamp**: 2026-07-17T21:40:00Z
- **Task Slug**: replace-default-handler-crash-activity

---

## 1. What was asked
The goal of this task was to extend the custom crash/failure UI pattern of `RecoveryActivity` to runtime uncaught exceptions. Specifically:
- Create `CrashActivity` and its corresponding XML layout `activity_crash.xml` verbatim as provided.
- Modify `GlobalExceptionHandler.kt` to launch `CrashActivity` immediately after writing local logs and sending reports to Firebase Crashlytics, instead of chaining to the system's default uncaught exception handler in the success path.
- Add 11 new `crash_` prefixed string resources in `strings.xml` sit directly after `recovery_copied`.
- Declare `CrashActivity` in the `AndroidManifest.xml` as a singleTask activity directly after `RecoveryActivity`.
- Address specific behavioral changes, testing considerations, and potential timing risks.

---

## 2. What actually changed

### A. New Files Created

1. **`app/src/main/java/com/inscopelabs/abx/server/core/diagnostics/CrashActivity.kt`**
   - Created verbatim as authoritative. 
   - Uses layout `R.layout.activity_crash`.
   - Safely retrieves exception metadata, exception type, error message, and stack trace from incoming intent extras.
   - Includes full copy-to-clipboard functionality for diagnostic details and a restart action that rebuilds task stacks to launch the application cleanly.
   - Implements robust error recovery (fallback to a Toast and finish) if the activity setup itself fails.

2. **`app/src/main/res/layout/activity_crash.xml`**
   - Created verbatim as authoritative.
   - Provides a clean, material-compliant scrollable diagnostics view.
   - Leverages platform default color attributes (`?android:attr/colorBackground`, `?android:attr/textColorPrimary`, `?android:attr/textColorSecondary`) to ensure flawless theme inheritance.
   - Fully utilizes `crash_` strings.

### B. Modified Files

1. **`app/src/main/java/com/inscopelabs/abx/server/core/diagnostics/GlobalExceptionHandler.kt`**
   - Removed the default chaining from the successful path of exception processing.
   - Added `launchCrashActivity()` which gathers device metadata, extracts stringified stack traces, sets up the `Intent` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`, starts `CrashActivity`, and marks a `@Volatile` flag `crashActivityLaunched = true`.
   - The fallback logic now checks `crashActivityLaunched` in the finally block: if `false` (indicating a failure to launch the custom UI), it chains to `defaultHandler` to prevent a silent freeze.
   - Concludes by unconditionally terminating the process using `Process.killProcess(Process.myPid())` and `exitProcess()`.

2. **`app/src/main/res/values/strings.xml`**
   - Added 11 new string resources sitting directly after the `recovery_copied` resource. All strings are prefixed with `crash_`.

3. **`app/src/main/AndroidManifest.xml`**
   - Declared `.core.diagnostics.CrashActivity` inside the `<application>` node, placed directly after `.boot.RecoveryActivity`.
   - Configured with `exported="false"` and `launchMode="singleTask"`.

---

## 3. Build & Compilation Results
- **Command Run**: `compile_applet`
- **Result**: **Build succeeded** - the full app compiles flawlessly against the Android SDK. `CrashActivity` resolves `R.layout.activity_crash` and all ID resources perfectly.

---

## 4. Architectural Analysis & Key Risks Identified

### A. The Intent Hand-off Timing Risk
Starting an activity and calling `Process.killProcess()` immediately afterward is a common pattern for custom crash activities. Because `startActivity()` asynchronously registers the launch request with Android's `system_server`, the OS is able to spin up a new process to host the requested activity even after the throwing process is killed.
* **Risk**: Under extreme memory/CPU stress or on non-standard Android OEM implementations, the process kill could execute before `system_server` processes the transaction, leading to a silent app termination or a blank screen.
* **Mitigation**: We did not introduce artificial delays (like `Handler.postDelayed`) prematurely to keep the code deterministic and lightweight. However, we strongly recommend manual validation of this hand-off on at least two distinct hardware platforms (e.g. Samsung OneUI and Stock Android). If flaky, we can introduce a small `postDelayed` loop of `50ms-150ms`.

### B. Suppression of Default Exception Chaining
* **Risk**: The major trade-off of this implementation is that skipping the default system handler prevents the OS from showing its standard crash dialog and sending system-level crash dumps to Google Play Console Vitals.
* **Mitigation**: Our implementation preserves Firebase Crashlytics delivery, which still catches and records these errors prior to terminating the process. Therefore, developer-visible error tracking remains fully functional, while the user receives a much more friendly and polished recovery experience.

### C. Theme Inheritance & XML Compatibility
* **Risk**: Since `CrashActivity` inherits the global theme (`Theme.MyApplication`) and uses pure Android view components rather than Compose UI, custom styling conflicts could arise.
* **Mitigation**: Confirmed that `Theme.MyApplication` uses standard Material Design attributes. By strictly referencing `?android:attr/colorBackground`, `?android:attr/textColorPrimary`, and `?android:attr/textColorSecondary` rather than hardcoded colors or separate theme files, we ensure the crash activity retains excellent contrast and adapts dynamically to any dark/light mode configurations of the OS.
