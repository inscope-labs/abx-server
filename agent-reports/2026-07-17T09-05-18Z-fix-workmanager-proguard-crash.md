# Process Report: Fix WorkManager and AndroidX Startup ProGuard Crash

- **Timestamp:** 2026-07-17T09:05:18Z
- **Task Slug:** fix-workmanager-proguard-crash

---

## 1. What was asked
The goal was to address a startup crash following the enabling of minification/R8 in Stage 17:
- **Observed Behavior**: The application crashed instantly on launch without throwing any visible Java exceptions or stack traces in standard logcat. Instead, the system logged a `data_app_crash` event from DropBoxManager and immediately terminated the process with a `SIGKILL` signals right after the system log: `"WM-WrkMgrInitializer: Initializing WorkManager with default configuration."`
- **Root Cause Hypothesis**: WorkManager auto-initialization utilizes `androidx.startup.InitializationProvider` (a ContentProvider), which is registered in the merged manifest and executes prior to `Application.onCreate()`. Under the hood, `androidx.startup` and `androidx.work` rely heavily on reflection to discover, load, and instantiate their respective initialization classes (such as `Configuration.Provider` and `Initializer` implementations) by their exact fully qualified class names. Without explicit ProGuard / R8 keep rules, these internal components or their initializers are stripped, renamed, or optimized away, leading to a silent initialization crash and process kill.
- **Goal**: Add explicit, targeted ProGuard rules keeping `androidx.work` and `androidx.startup` classes, interface implementations, and initializers intact under R8 minification.
- **Constraints**:
  - Do not modify or revert `isMinifyEnabled = true`, `isShrinkResources = false`, or `proguardFiles` configuration inside `app/build.gradle.kts`.
  - Appended rules only to `/app/proguard-rules.pro` without removing or reordering any pre-existing configurations.
  - Followed all `AGENTS.md` rules, including drift protection checks before performing modifications.

---

## 2. Drift-Protection Audit
- **Action**: Queried remote `app/proguard-rules.pro` file content from GitHub's live repository.
- **Finding**: Verified 100% parity between local working copy and GitHub remote main prior to editing. No active drift detected.

---

## 3. Changes Applied

### `app/proguard-rules.pro`
Appended the following WorkManager and AndroidX Startup reflection protection rules to the end of the file:
```proguard
# WorkManager / AndroidX Startup Rules
# WorkManager and androidx.startup use reflection to discover their
# own Configuration.Provider / Initializer classes; without these
# rules R8 can strip or rename internals they depend on by exact
# name, causing an immediate, stacktrace-less crash at process
# startup (before Application.onCreate()).
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }
-keep class * extends androidx.startup.Initializer {
    public <init>();
}
-keepclassmembers class * implements androidx.work.Configuration$Provider {
    public *;
}
-dontwarn androidx.work.**
```

---

## 4. Verification and Next Steps
- **Compilation**: Ran `compile_applet` successfully. The build compiles without warnings or errors.
- **Testing Warning / Hypothesis Status**: This change constitutes a targeted, highly plausible hypothesis to resolve the observed stacktrace-less `SIGKILL` crash. If the crash persists under minified release builds during the next build cycle, we will rule out this theory and proceed to isolate other potential reflection-dependent library boundaries (such as Ktor, custom native bindings, or platform integrations).
