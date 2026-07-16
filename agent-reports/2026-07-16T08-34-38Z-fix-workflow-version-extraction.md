# Process Report: Fix workflow version extraction from version.properties

- **Timestamp:** 2026-07-16T08:34:38Z
- **Task Slug:** fix-workflow-version-extraction

---

## 1. What was asked
- Fix the issue in `.github/workflows/build-apk-debug.yml` where a step failed during log initialization with exit code 1.
- This failure occurred because of outdated greps in the script trying to parse literals `versionCode` and `versionName` directly from `app/build.gradle.kts` after those values were replaced with dynamic loaders pointing to `version.properties`.

---

## 2. Root Cause Analysis
In the previous stage, `app/build.gradle.kts` was successfully changed to parse `versionCode` and `versionName` dynamically from the central `version.properties` properties file.
However, `.github/workflows/build-apk-debug.yml` had a pre-existing diagnostic step "Initialize build log & display build info" that executed:
```bash
VERSION_CODE=$(grep -m1 'versionCode' app/build.gradle.kts | grep -oE '[0-9]+')
VERSION_NAME=$(grep -m1 'versionName' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')
```
Since literal numbers/quoted values are no longer hardcoded in `app/build.gradle.kts`, `grep -oE '[0-9]+'` and `grep -oE '"[^"]+"'` returned no matches. In a shell environment with `set -eo pipefail`, a failed pipe step (where `grep` returns exit status 1 due to no match) immediately terminated the workflow script with a non-zero exit status, failing the build pipeline.

---

## 3. Changes Implemented

### `.github/workflows/build-apk-debug.yml`
Updated the version resolution lines in the "Initialize build log & display build info" step to safely parse key-value pairs directly from the single source of truth `version.properties` instead:
```yaml
          VERSION_CODE=$(grep -m1 'versionCode' version.properties | cut -d'=' -f2 | tr -d ' ')
          VERSION_NAME="$(grep -m1 'versionMajor' version.properties | cut -d'=' -f2 | tr -d ' ').$(grep -m1 'versionMinor' version.properties | cut -d'=' -f2 | tr -d ' ').$(grep -m1 'versionDebug' version.properties | cut -d'=' -f2 | tr -d ' ')"
```
This avoids pattern-matching file paths dynamically with brittle assumptions and reads raw properties directly.

---

## 4. Verification and Results
- The local applet builds and compiles successfully.
- No other functional files were touched.
- The workflow now cleanly extracts version info from `version.properties`.
