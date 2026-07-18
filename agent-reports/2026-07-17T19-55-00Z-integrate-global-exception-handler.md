# Architecture and Task Report: Integration of Global Exception Handling Framework

- **Timestamp**: 2026-07-17T19:55:00Z
- **Task Slug**: integrate-global-exception-handler
- **Lead Architect**: ABX Platform Lead Android Software Architect

---

## 1. Executive Summary & Objectives
The goal of this task was to integrate a production-grade, highly resilient Global Exception Handling Framework (`GlobalExceptionHandler`) into the ABX Platform application. The system must seamlessly intercept any unexpected runtime exceptions, write structured local diagnostic reports with bounded size (log rotation), gracefully handle Firebase Crashlytics if available dynamically, and delegate back to the platform's default handler without introducing startup failures, recursion loops, or performance bottlenecks.

---

## 2. Project-Wide Audit Findings
Before implementation, we completed a project-wide security and stability audit across all modules (`:app`, `:core:keystore`, `:core:audit`, `:core:session`, `:core:tunnel`, `:core:policy`, `:core:filesystem`, `:core:mcp`):
1. **Existing Uncaught Handlers**: Our search confirmed that **no other custom uncaught exception handlers** existed in the codebase.
2. **Startup Diagnostics**: Startup errors are safely wrapped inside `HelloApplication`'s `try-catch` blocks and recorded via `BootGuard` to a persistent database/shared preference. This allows `MainActivity` to safely redirect to a dedicated `RecoveryActivity` for user-visible diagnostics and self-healing retries.
3. **Swallowed Exceptions**:
   - Analyzed all catch blocks. Swallowed exceptions are only present in critical failure-reporting paths (`BootGuard`, `RecoveryActivity`, and inside our newly designed `GlobalExceptionHandler`) where throwing would trigger recursion. This is correct, intentional, and robust.
4. **Resource Management**: Checked `AuditLog` and file-writing segments. Checked `TunnelService` coroutines and WebSocket resources. All files are manipulated via safe idiomatic operations (e.g. `forEachLine`, `.use {}` blocks) that automatically guarantee closing. No resource leaks were detected.

---

## 3. Files Created & Modified

### A. New File: `/app/src/main/java/com/inscopelabs/abx/server/core/diagnostics/GlobalExceptionHandler.kt`
Designed a fully synchronized uncaught exception handler in the `com.inscopelabs.abx.server.core.diagnostics` package. It features:
* **Log Rotation**: Limits storage space to `5MB` (`MAX_LOG_SIZE`). If a new crash report threatens to exceed this threshold, the file is rotated (copied to `crash_logs.txt.1`, deleting older entries) and a fresh file is created.
* **Metadata Extraction**: Extracted complete details including thread information, package names, `BuildConfig` versions, full stack traces, nested exception causes, OS/API levels, and device manufacturers.
* **Dynamic Crashlytics Delegation**: Uses safe Java reflection to detect and interact with Firebase Crashlytics if present on the classpath. If Firebase is not present (which is the case for local or placeholder configurations), it degrades silently with zero performance impact or ClassNotFound crashes.
* **Graceful Chaining**: Guarantees invocation of the platform's default handler in a final block, preventing lockups.

### B. Modified File: `/app/src/main/java/com/inscopelabs/abx/server/HelloApplication.kt`
Integrated the handler as the absolute first action in `HelloApplication.onCreate()`, preserving all existing startup diagnostics, `KeyStoreManager` initialization, and `AuditLog` setup.

---

## 4. Startup Order and Integration Points
1. **OS Process Load**: Application starts, classloaders load `HelloApplication`.
2. **Base Context Attachment**: `HelloApplication.attachBaseContext()` executes.
3. **Application Birth**: `HelloApplication.onCreate()` is called.
4. **Crash Handler Installation**: `Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))` is registered.
5. **Stage 1 (KeyStoreManager)**: KeyStore initialized under try-catch; failure is recorded in `BootGuard`.
6. **Stage 2 (AuditLog)**: Secure audit logger initialized under try-catch; failure is recorded in `BootGuard`.
7. **Main Interface Launch**: `MainActivity` starts. If `BootGuard` has any failure, `BootRoute` instantly redirects the user to `RecoveryActivity`.

---

## 5. Verification & Compilation Results
* Performed a complete code compilation via the platform's incremental build runner.
* **Status**: **Build succeeded** - the applet compiles successfully!

---

## 6. Risks Identified & Mitigation Actions
* **Risk: R8/ProGuard Stripping**: R8 might shrink or obfuscate the crash handler class or fields.
  - *Mitigation*: Our audit of `proguard-rules.pro` confirmed the rule `-keep class com.inscopelabs.abx.server.** { *; }` is active, which guarantees the handler is fully preserved in release builds.
* **Risk: File Access Deadlocks**: Writing from multiple concurrent threads during a multi-thread crash could lead to deadlocks or file corruption.
  - *Mitigation*: Synchronized log file creation and writing using a dedicated JVM-level lock `fileLock`.
* **Risk: Memory Leaks**: Holding a strong reference to short-lived `Context` inside a JVM-wide exception handler can lead to garbage collection leaks.
  - *Mitigation*: Resolved by extracting and holding only the long-lived `applicationContext` reference (`context.applicationContext`).
