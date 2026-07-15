# Process Report: Stage 12 — Networking layer: :core:tunnel + :core:session modules

- **Timestamp**: 2026-07-15T02:45:00Z
- **Task**: Stage 12 of 17 — Networking layer: :core:tunnel + :core:session modules
- **Repo**: inscope-labs/abx-server

## 1. What was asked
Set up the real networking layer in `abx-server` by introducing both `:core:session` and `:core:tunnel` modules with full implementations ported verbatim from `abx-mcp` (except package adjustments).
Requirements:
1. Add `workManager = "2.9.1"` and `androidx-work-runtime` to `gradle/libs.versions.toml`.
2. Include `:core:session` and `:core:tunnel` in `settings.gradle.kts`.
3. Create the module configuration `core/session/build.gradle.kts` and empty `AndroidManifest.xml`.
4. Port the 8 session files verbatim from `abx-mcp`, renaming package/imports to `com.inscopelabs.abx.server.core.*`.
5. Create the module configuration `core/tunnel/build.gradle.kts` and ported `AndroidManifest.xml`.
6. Port the 9 tunnel files verbatim, renaming package/imports and renaming hardcoded status notifications to the `ABX` identity.
7. Add `android.permission.INTERNET` to `/app/src/main/AndroidManifest.xml`.
8. Add the necessary dependencies to `/app/build.gradle.kts`.
9. Ensure `versionCode` remains unchanged at `3`.
10. Confirm no active code calls `SessionManager`, `TunnelManager`, or `TunnelService` yet.

## 2. Drift Protection Results
- Fetched the live versions of:
  - `settings.gradle.kts`
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `app/src/main/AndroidManifest.xml`
- Verified complete match with no remote or local drift before applying changes.

## 3. Files Created and Modified
The following directories and files were created:
- **`core/session/build.gradle.kts`**
- **`core/session/src/main/AndroidManifest.xml`**
- **`core/session/src/main/java/com/inscopelabs/abx/server/core/session/`**:
  - `SessionState.kt`
  - `UserGesture.kt`
  - `ReplayProtection.kt`
  - `ReplayProtectionImpl.kt`
  - `ReplayProtectionProvider.kt`
  - `SessionManager.kt`
  - `SessionManagerImpl.kt`
  - `SessionManagerProvider.kt`
- **`core/tunnel/build.gradle.kts`**
- **`core/tunnel/src/main/AndroidManifest.xml`**
- **`core/tunnel/src/main/java/com/inscopelabs/abx/server/core/tunnel/`**:
  - `TransportProvider.kt`
  - `Message.kt`
  - `WebSocketTransport.kt`
  - `FakeTransportProvider.kt`
  - `TunnelManager.kt`
  - `TunnelManagerImpl.kt`
  - `TunnelManagerProvider.kt`
  - `TtlCheckWorker.kt`
  - `TunnelService.kt`

The following files were modified:
- **`gradle/libs.versions.toml`**: Added WorkManager version & library definitions.
- **`settings.gradle.kts`**: Included `:core:session` and `:core:tunnel` modules.
- **`app/src/main/AndroidManifest.xml`**: Added `android.permission.INTERNET`.
- **`app/build.gradle.kts`**: Integrated dependencies on `:core:session`, `:core:tunnel`, Retrofit, Moshi, OkHttp, and Kotlinx Coroutines.

## 4. Key Constraints and Exclusions Verified
- **Unchanged versionCode**: Confirmed `versionCode` remains exactly `3` with zero bump this stage.
- **Applied-but-Unused Dependencies**: Confirmed Retrofit and Moshi dependencies are added to `/app/build.gradle.kts` but currently remain applied-but-unused in any `.kt` file, mirroring `abx-mcp` precisely.
- **No Invocation by App Source**: Verified that neither `HelloApplication.kt`, `MainActivity.kt` nor other app source files call into `SessionManager`, `TunnelManager`, or start `TunnelService` yet.
- **Special Use FGS Property**: Verified `TunnelService` is declared in the `:core:tunnel` manifest with foreground service type `specialUse` and its associated property matches specifications.

## 5. Build Verification
- Ran `compile_applet` which successfully compiled the entire codebase with the two new modules on the first attempt.
