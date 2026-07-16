# Process Report: Resolve Gradle 9.6.1 Project Dependency Deprecation and Android Lint Error

- **Timestamp:** 2026-07-16T08:57:31Z
- **Task Slug:** resolve-gradle9-project-dependency-deprecation-and-lint-error

---

## 1. What was asked
The user provided a snippet of the CI execution log where:
1. Android Lint failed during compile/validation:
   - Error: When targeting Android 13 or higher, posting a permission requires holding the `POST_NOTIFICATIONS` permission (usage from `com.inscopelabs.abx.server.core.tunnel.TunnelService.onCreate.1.1`) `[NotificationPermission]`
2. A prominent Gradle 9.6.1 deprecation warning was logged:
   - "Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10. Please use the project(String) method on DependencyHandler or the createProjectDependency(String) method on DependencyFactory instead."

---

## 2. Findings and Root Cause Analysis

### Deprecation Warning Root Cause
- In Gradle's Kotlin DSL, inside the `dependencies { ... }` block, the implicit receiver is `DependencyHandlerScope`.
- However, since `build.gradle.kts` runs in the context of `Project`, the member function `Project.project(path: String): Project` is also in the outer scope.
- In Kotlin, a member function of an outer receiver (`Project`) takes precedence over an extension function on an inner receiver (`DependencyHandler.project(path: String): ProjectDependency`).
- Consequently, calling `project(":some-module")` inside the dependencies block was returning a `Project` object instead of a `ProjectDependency` notation, triggering Gradle 9's deprecation warning.
- **Solution**: Explicitly reference `this` (which binds directly to the `DependencyHandlerScope` / `DependencyHandler` context) via `this.project(...)` or `dependencies.project(...)`. This bypasses the outer `Project` member scope and correctly resolves to the expected `ProjectDependency` builder.

### Android Lint Error Root Cause
- The application targets SDK 36 (Android 16), which is Android 13+.
- In `TunnelService` inside the `:core:tunnel` module, local status notifications are posted.
- Targeting Android 13+ requires holding the `android.permission.POST_NOTIFICATIONS` permission in the app's `AndroidManifest.xml` when notifications are posted, which was missing from `/app/src/main/AndroidManifest.xml`.
- **Solution**: Added `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` to `AndroidManifest.xml`.

---

## 3. Changes Applied

### Gradle Files
Modified the following files to change all occurrences of `project(":...")` to `this.project(":...")` inside the `dependencies` block:
- `/app/build.gradle.kts`
- `/core/tunnel/build.gradle.kts`
- `/core/session/build.gradle.kts`
- `/core/audit/build.gradle.kts`
- `/core/policy/build.gradle.kts`
- `/core/mcp/build.gradle.kts`

### Manifest
Added the required notification permission to:
- `/app/src/main/AndroidManifest.xml`

---

## 4. Verification and Results
1. **GitHub Drift Protection Compliance**: Fetched the live versions of the protected paths (`app/build.gradle.kts` and `app/src/main/AndroidManifest.xml`) from GitHub and verified no local conflicts.
2. **Compilation Verification**: Confirmed that `compile_applet` runs and completes successfully.
3. **Lint Verification**: Executed the `gradle :app:lintDebug --warning-mode all` task:
   - **Result**: `BUILD SUCCESSFUL in 28s`
   - **Deprecations check**: Verified `build.log` contains zero `Project object as a dependency notation` or other deprecation warnings.
   - **Lint check**: Successfully generated a clean lint report with zero lint errors.
