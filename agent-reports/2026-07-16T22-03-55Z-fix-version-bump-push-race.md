# Process Report: Fix Version-Bump Push Race Condition in CI/CD Workflow

- **Timestamp:** 2026-07-16T22:03:55Z
- **Task Slug:** fix-version-bump-push-race

---

## 1. What was asked
The goal was to resolve a silent version-bump race condition occurring during concurrent execution of the debug build workflow:
1. Identify the root cause of why the workflow frequently reported the identical version bump (`versionCode -> 5, versionDebug -> 46`) across different runs.
2. Replace the unsafe Git commit, pull, and push steps inside the "Commit build log" step in `.github/workflows/build-apk-debug.yml` with a robust, self-healing loop.
3. Configure the self-healing push loop to:
   - Handle concurrent pushes gracefully with up to 5 fetch-and-rebase retry attempts.
   - Resolve conflicts on `version.properties` by choosing the higher of the two values (`versionCode` or `versionDebug`) rather than crashing or skipping the push.
   - Fail loudly with a GitHub Actions error indicator (`::error::`) if it still fails to push after 5 attempts.
4. Adhere to `AGENTS.md` drift protection by querying remote git branch main parity prior to the edit.
5. Identify and flag other workflow files that likely contain the same bug, but do not modify them to maintain strict scope discipline.

---

## 2. Root Cause Analysis
In the previous CI/CD setup, the version bump process was defined as:
```bash
git add "$LOGFILE" version.properties
git commit -m "ci: bump version to (versionDebug $NEW_VERSION_DEBUG) versionCode=$NEW_VERSION_CODE"
git pull --rebase origin main
git push
```
- **The Race Condition**: If two workflows run concurrently, they both increment the local `version.properties` and then attempt to pull/push.
- **Silent Failure**: Since Git runs in a shell with standard configuration, when `git pull --rebase origin main` encountered a conflict on the `version.properties` file, it would abort or block. Under the default shell script execution settings, any error in the rebase stage caused the script/step to terminate immediately before reaching `git push`.
- **Result**: The local bump was never pushed to the remote repository. The remote `version.properties` stayed stagnant, causing all subsequent workflow executions to start from the same stale base, recalculate the same version numbers, and repeatedly produce the identical version-bump log outputs.

---

## 3. Changes Applied

### `.github/workflows/build-apk-debug.yml`
Replaced the "Commit build log" step with a robust 5-attempt loop:
- **Fetch & Rebase**: Retries the push up to 5 times. If rejected, it fetches and rebases.
- **Automatic Conflict Resolution**: If a rebase conflict occurs on `version.properties`, it performs `git checkout --theirs version.properties` to read the remote value. It parses the remote `versionCode` and compares it to the local `NEW_VERSION_CODE`.
  - If the remote `versionCode` is greater than or equal to the local bump, it skips the local bump (since a concurrent run already advanced the version code past or equal to ours) by running `git rebase --skip`.
  - Otherwise, it resolves in favor of our higher local bump and continues the rebase.
- **Fail-Loud**: If all 5 attempts fail, it exits with exit code 1 and prints a clear `::error::` annotation.

---

## 4. Latent Risks in Other Workflows
The following workflows share the exact same raw `git pull --rebase / git push` mechanism and are highly susceptible to the identical race condition:
1. `.github/workflows/build-apk-release.yml`
2. `.github/workflows/build-aab-bundle.yml`

As instructed under strict scope discipline, these files were **not** modified during this task. They should be addressed in subsequent discrete tasks.

---

## 5. Verification and Results
1. **Drift Protection Check**: Fetched the live version from GitHub remote and confirmed perfect parity with the local file before modifying.
2. **Linter and Compilation Status**: Confirmed that `compile_applet` compiles cleanly after the change.
