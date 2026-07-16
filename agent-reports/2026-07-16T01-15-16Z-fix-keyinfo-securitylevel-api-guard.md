# Process Report: Fix incorrect API-level guard on KeyInfo.getSecurityLevel()

- **Timestamp:** 2026-07-16T01:15:16Z
- **Task Slug:** fix-keyinfo-securitylevel-api-guard

---

## 1. What was asked
- Fix the incorrect API-level guard on `KeyInfo.getSecurityLevel()` inside `app/src/main/java/com/inscopelabs/abx/server/EnrollmentScreen.kt`.
- The current guard checked `Build.VERSION_CODES.P` (API 28), but `getSecurityLevel()` was actually added in `Build.VERSION_CODES.S` (API 31), causing a fatal `NoSuchMethodError` on real devices running API 28-30 (Android 9/10/11).
- Correct this guard to target API 31 (`Build.VERSION_CODES.S`) and add an `else if` branch for API 28-30 that gracefully falls back to `isStrongBoxBacked = false`.
- Do not touch any other files or any other logic.
- Follow `AGENTS.md` drift-protection (fetch live content of `EnrollmentScreen.kt` first).
- Write a report documenting the fix.

---

## 2. Root Cause Analysis
The class `android.security.keystore.KeyInfo` exposed `getSecurityLevel()` starting with Android 12 (API level 31, `Build.VERSION_CODES.S`). Attempting to access `keyInfo.securityLevel` on devices running Android 9 through Android 11 (API levels 28 to 30) leads to a fatal runtime crash:
`java.lang.NoSuchMethodError: No virtual method getSecurityLevel()I in class Landroid/security/keystore/KeyInfo;`

This bug was pre-existing in the original `abx-mcp` implementation and was not introduced during the porting process.

---

## 3. Changes Applied

### `app/src/main/java/com/inscopelabs/abx/server/EnrollmentScreen.kt`
Updated the guard block where `keyInfo.securityLevel` is retrieved to ensure it only executes on API level 31 (`Build.VERSION_CODES.S`) and above. For older supported hardware-backed API versions (such as API 28 to 30), it falls back to a safe default `isStrongBoxBacked = false` since we cannot directly inspect specific StrongBox containment without calling `getSecurityLevel()`.

```kotlin
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        isStrongBoxBacked = keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        // Pre-API-31: cannot query securityLevel directly. Fall back to
                        // the general hardware-backed signal; this cannot distinguish
                        // StrongBox from TEE on these API levels.
                        isStrongBoxBacked = false
                    }
```

---

## 4. Verification and Results
- Prior to editing, we checked for drift using GitHub raw content. There was absolutely zero drift.
- Compiled the applet successfully, verifying clean compilation under Jetpack Compose and modern Kotlin standards.
- Handled API level safety correctly.
