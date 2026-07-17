# Process Report: CI-Owned Paths Protection and Blanket-Staging Prevention

- **Timestamp:** 2026-07-16T22:34:00Z
- **Task Slug:** agents-md-ci-owned-paths-fix

---

## 1. What was asked
The goal was to resolve a critical Git staging bug in the AI Studio environment that has been impacting the GitHub repository's integrity:
1. Explain the root cause of how unrelated AI Studio commits were inadvertently reverting `version.properties` and deleting newly generated CI/CD run logs (`build-logs/*.log`).
2. Add explicit guidelines and rules directly to `AGENTS.md` specifying that:
   - certain paths (such as `version.properties` and files in `build-logs/`) are exclusively CI-owned, and must never be edited, staged, or deleted.
   - blanket-staging commands (e.g. `git add -A`, `git add .`) are strictly forbidden. Instead, only explicit file paths representing modified files should be staged and committed.
3. Check and verify parity with the live `AGENTS.md` file on remote `main` before applying any edits to guarantee drift protection.

---

## 2. Root Cause Analysis

### Staging of Stale Local Files
When the AI Studio agent executes git operations, it has historically used blanket-staging commands like `git add .` or `git add -A` to stage changes before committing and pushing. However, because AI Studio has no local `git pull` or `git fetch` capabilities, the local workspace copies of several dynamically generated or updated files can easily become stale.
- **Reversion of `version.properties`**: Every time a GitHub Action executes, it automatically increments `versionCode` or `versionDebug` inside `version.properties` on remote `main`. However, because this workspace never receives those updates, the local workspace's copy of `version.properties` remains stale. When a blanket `git add .` is executed, the stale local `version.properties` is staged, committed, and pushed, overwriting the correctly updated remote values and reverting the project's build version.
- **Deletion of CI/CD Run Logs**: GitHub Actions write to `/build-logs` directory upon completion. Since the local workspace lacks those log files, running `git add -A` / `git add .` treats the missing local paths as deletions, staging them as `deleted:` and permanently wiping recent build logs on push.

### The Fix
To prevent this, we must adopt surgical staging and treat `version.properties` and the `build-logs/` directory as completely hands-off. No blanket-staging is allowed under any circumstances.

---

## 3. Drift-Protection Audit
- **Action**: Fetched the live version of `AGENTS.md` using `curl` and diffed it against `/AGENTS.md`.
- **Finding**: No diff was produced, confirming 100% parity with remote main and no active drift.

---

## 4. Changes Applied

### `AGENTS.md`
Added two new sub-sections directly after the GitHub Drift Protection section:
- **`1a. CI-Owned Paths — Never Edit, Never Stage, Never Delete`**: Defines `version.properties` and `build-logs/**` as exclusively CI-owned. Explicitly forbids modifying or staging them under any circumstances.
- **`1b. Never Stage or Commit the Entire Working Tree`**: Expressly prohibits any commands like `git add -A` or `git add .`. Mandates that agents only stage files one-by-one or as an explicit list of paths, checking `git status` beforehand to ensure absolute compliance.

---

## 5. Verification and Results
- **Compilation check**: Ran `compile_applet` successfully to verify that formatting of Markdown and overall structure builds correctly.
- **Rules Enforcement**: Confirming that all subsequent commits from the AI Studio agent will adhere to surgical, explicit file-level staging.
