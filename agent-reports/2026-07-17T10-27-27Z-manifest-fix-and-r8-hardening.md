# Process Report: Android Manifest Fixes and Anti-Silent-Killer ProGuard Hardening

- **Timestamp:** 2026-07-17T10:27:27Z
- **Task Slug:** manifest-fix-and-r8-hardening

---

## 1. What was asked
We were tasked with implementing two key enhancements to the application to prevent silent startup crashes and restore missing functional integrations:
1. **Part A: Manifest Fixes**:
   - Add `android:launchMode="singleTop"` to the `<activity>` tag of `MainActivity` in `app/src/main/AndroidManifest.xml`.
   - Add a second `<intent-filter>` to `MainActivity` supporting `android.intent.action.SEND` for `text/plain` MIME types. This makes the existing `onNewIntent` and `handleIntent` implementation in `MainActivity.kt` functional and reachable.
2. **Part B: Diagnostic Flags**:
   - Append print directives at the top of `/app/proguard-rules.pro` to dump R8 configurations, seeds, and usage to custom files (`full-r8-config.txt`, `seeds.txt`, `usage.txt`) under `build/outputs/mapping/release/` during release builds.
3. **Part C: Comprehensive Anti-Silent-Killer Hardening**:
   - Append comprehensive ProGuard keep and suppression rules to guard reflection-based components (AndroidX Startup, WorkManager worker instantiations, Kotlin Coroutines, Jetpack Compose runtime internals, ZXing MultiFormatWriter) from being stripped or renamed.

---

## 2. Root Cause Hypothesis
The application's minified release configuration suffered an instant launch-time crash (SIGKILL). Logcat analysis indicated the crash occurred within ~10ms of WorkManager's automatic initialization under `androidx.startup.InitializationProvider` before `Application.onCreate()`.

### The reflection failure:
- **AndroidX Startup**: Initializer classes are discovered using reflection over `<meta-data>` strings in the AndroidManifest, rather than standard class declarations. Standard AAPT2 auto-keep configurations do not protect these, leading to R8 stripping the initializers.
- **WorkManager**: Background worker lookups (e.g. for resuming tasks across app launches) use reflection over fully qualified worker class names.
- **Kotlin Coroutines / Compose Runtime**: Internal Continuation factories and state managers rely heavily on reflection and ServiceLoader mechanisms.

Adding explicit keep rules preserves these key runtime pathways.

---

## 3. Drift-Protection Audit
- **Files Checked**: `app/src/main/AndroidManifest.xml` and `app/proguard-rules.pro`.
- **Finding**: Absolute parity with remote `main` branch; no drift detected.

---

## 4. Changes Applied

### Part A: Manifest Fixes (`app/src/main/AndroidManifest.xml`)
Modified the `MainActivity` definition to support `singleTop` launch mode and handle standard plain-text send intents:
```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:label="@string/app_name"
            android:theme="@style/Theme.MyApplication">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
```

### Part B & C: ProGuard Rules (`app/proguard-rules.pro`)
Prepend Diagnostic Flags at the top:
```proguard
# Diagnostic output — inspect build/outputs/mapping/release/ after
# each build to see exactly what R8 kept, removed, and why.
-printconfiguration build/outputs/mapping/release/full-r8-config.txt
-printseeds build/outputs/mapping/release/seeds.txt
-printusage build/outputs/mapping/release/usage.txt
```

Append Hardening Rules at the bottom:
```proguard
# ===== Anti-silent-killer hardening pass =====
# AAPT2/R8 already auto-keep real manifest components (Activity,
# Service, Receiver, Provider declared via android:name on a
# component tag) — do NOT add manual rules for those, it's
# redundant. The rules below target genuine reflection paths that
# are NOT auto-protected.

# androidx.startup: Initializer classes are discovered via
# <meta-data android:name="..."> STRING values inside
# InitializationProvider's manifest entry, not as a component tag
# — this reflection path is NOT covered by AAPT2's auto-keep.
-keep class * extends androidx.startup.Initializer {
    public <init>();
}
-keep class androidx.startup.InitializationProvider { *; }

# WorkManager: Configuration.Provider is discovered via an
# `application is Configuration.Provider` check — safe under R8
# on its own — but WorkManager's internal Room-backed work
# database and its own Worker-class-by-name lookup (used when
# resuming persisted work after process death) both rely on
# reflection separate from the app's own Room rules above.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# Kotlin coroutines: Continuation/suspend-function machinery and
# CoroutineExceptionHandler service-loader discovery.
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.**

# Compose: bundled consumer rules normally handle this, but keep
# Compose runtime's reflection-based slot-table/snapshot classes
# explicitly to rule this out as a variable.
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# zxing: pure-Java, no reflection, but the MultiFormatWriter's
# internal encoder lookup uses a switch-on-enum pattern R8 can
# sometimes miscompile in older versions — keep defensively.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
```

---

## 5. R8 Diagnostics and Build Artifacts
Because the standard `compile_applet` tool compiles the local workspace under the `debug` build variant (where minification/R8 is disabled), R8 is not executed locally, and the release mapping files are not generated in this temporary environment.

However, once these changes are pushed to GitHub, the CI/CD workflow will generate the `release` build variant and trigger full R8 processing. The output files to inspect inside the CI runner are:
- `app/build/outputs/mapping/release/full-r8-config.txt`: The complete resolved ProGuard rules configuration applied by R8.
- `app/build/outputs/mapping/release/seeds.txt`: The definitive list of classes, fields, and methods that were explicitly kept by the configuration.
- `app/build/outputs/mapping/release/usage.txt`: The list of all classes, fields, and methods that were stripped/removed.

If the launch-time crash persists, inspecting these files will pinpoint exactly whether an essential class was stripped.
