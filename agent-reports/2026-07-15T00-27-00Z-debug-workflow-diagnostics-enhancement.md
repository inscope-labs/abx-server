# Process Report: Debug Workflow Diagnostics Enhancement

- **Timestamp**: 2026-07-15T00:27:00Z
- **Task**: Enhance build-apk-debug.yml with linting, build-info display, and full diagnostics
- **Repo**: inscope-labs/abx-server
- **File**: `.github/workflows/build-apk-debug.yml`

## 1. What was asked
Enhance the debug build workflow with comprehensive linting, build-info display, and diagnostic logs/bundles (including manifest diagnostics, apk contents list, and a dependency report) while ensuring `diagnostics/` is artifact-only and `build-logs/debug/` remains the only committed path.

## 2. Drift Protection Result
- Fetched the live current content from GitHub (`https://raw.githubusercontent.com/inscope-labs/abx-server/main/.github/workflows/build-apk-debug.yml`).
- Compared with the local copy and verified that there was zero drift.

## 3. Workflow Steps Enhanced / Added
The following steps have been added or updated in `.github/workflows/build-apk-debug.yml`:
1. **Initialize build log & display build info**:
   - Creates the necessary directories.
   - Extracts `versionCode` and `versionName` directly from `app/build.gradle.kts`.
   - Displays metadata such as Run ID, Commit SHA, Ref, Triggered by, and Timestamp.
2. **Android Lint**: Runs `:app:lintDebug` with warnings and error outputs redirected and saved in `diagnostics/reports/lint.log`.
3. **ktlint**: Scans if ktlint configuration exists and runs ktlint checks, writing output to `diagnostics/reports/ktlint.log`.
4. **detekt**: Scans if detekt configuration exists and runs detekt analysis, writing output to `diagnostics/reports/detekt.log`.
5. **Unit Tests**: Runs Unit Tests via `:app:testDebugUnitTest`, saving logs to `diagnostics/reports/unit-tests.log`.
6. **Generate Dependency Report**: Runs `:app:dependencies` targeting `debugRuntimeClasspath` and writes reports to `diagnostics/dependencies.txt`.
7. **Collect Manifest Diagnostics**: Automatically locates and copies merged Android manifests from `app/build` into `diagnostics/manifest/`.
8. **Inspect APK Contents**: Uses `unzip -l` on the generated APK file to catalog its contents, outputting to `diagnostics/apk/apk-contents.txt`.
9. **Upload Diagnostics Bundle**: Collects all elements in the `diagnostics/` folder and uploads them as a zip artifact (`abx-server-debug-diagnostics`), set to retain for 14 days.

## 4. Diagnostics/ Commits Guard Confirmations
- **diagnostics/**: Confirmed that this directory is fully artifact-only. It is **NOT** added or committed to git.
- **build-logs/debug/**: Confirmed that `build-logs/debug/` is the only directory targeted by `git add "$LOGFILE"` and committed back to the repository.
