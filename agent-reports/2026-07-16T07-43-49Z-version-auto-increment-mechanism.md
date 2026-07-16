# Process Report: Auto-Incrementing version.properties Versioning Mechanism

- **Timestamp:** 2026-07-16T07:43:49Z
- **Task Slug:** version-auto-increment-mechanism

---

## 1. Summary of Accomplishments
We have successfully implemented a unified, centralized, and auto-incrementing Android versioning mechanism across all three GitHub Actions deployment workflows.

---

## 2. Changes Implemented

### A. Centralized Version Source (`/version.properties`)
Created a property configuration file at the repository root with the following initial bootstrap configurations:
```properties
versionMajor=0
versionMinor=0
versionDebug=45
versionCode=4
```
- **`versionCode`**: Bootstrapped at `4` to ensure absolute continuity with previous builds.
- **`versionName`**: Formatted dynamically as `major.minor.debug` (e.g. `0.0.45`), providing fine-grained version tracking.

### B. App build Gradle (`/app/build.gradle.kts`)
- Loaded properties from the root-level `version.properties` file during build execution.
- Dynamically assigned:
  - `versionCode` using `versionCode = versionProps.getProperty("versionCode").trim().toInt()`
  - `versionName` using `versionName = "$verMajor.$verMinor.$verDebug"`

### C. GitHub Workflows Integration
Updated the three deployment workflows:
1. **`.github/workflows/build-apk-debug.yml`**
2. **`.github/workflows/build-apk-release.yml`**
3. **`.github/workflows/build-aab-bundle.yml`**

In each of these workflows:
- Added a `Bump version` step immediately after checking out the source code and before any Gradle build steps. This step runs a bash shell script to parse the current `versionCode` and `versionDebug`, increments both values by 1, saves them back to `version.properties`, and exports them to `$GITHUB_ENV`.
- Ensured permissions for `build-apk-release.yml` and `build-aab-bundle.yml` are configured with `contents: write` so they can write back version updates.
- Configured each workflow to commit the updated `version.properties` file back to the repository on successful runs:
  - In `build-apk-debug.yml`, extended the existing "Commit build log" step to also stage `version.properties` in the exact same commit and pull via `--rebase origin main` before pushing.
  - In `build-apk-release.yml` and `build-aab-bundle.yml`, added a brand-new `Commit version bump` step that commits `version.properties` and pulls via `--rebase origin main` before pushing.

---

## 3. Design Principles & Safeguards
- **Shared Counter Principle**: `versionCode` and `versionDebug` increments on *any* successful run of *any* of the three workflows. This guarantees that `versionCode` remains strictly monotonically increasing and globally unique across debug, release, and AAB artifacts, satisfying App Store / Play Console and local upgrade install constraints.
- **Untouched Manual Segments**: `versionMajor` and `versionMinor` are manual-only fields. Automation does not touch, modify, or auto-increment them under any circumstances.
- **Build Continuity**: Tested compilation locally to verify that all Gradle property mapping logic executes seamlessly.
