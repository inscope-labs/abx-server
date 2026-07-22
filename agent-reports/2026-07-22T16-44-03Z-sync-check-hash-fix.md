# Agent Task Report: Sync Check Hash Fix

- **Timestamp**: 2026-07-22T16:44:03Z
- **Task Slug**: sync-check-hash-fix
- **Status**: Completed successfully

---

## 1. Description & Context
Fixed a confirmed bug in `tools/sync_check.py` where `sha1_file()` previously computed a standard SHA-1 hash over raw file bytes (`hashlib.sha1(data).hexdigest()`), whereas GitHub's Git Trees API returns git blob object hashes (`SHA-1("blob " + len(data) + "\0" + data)`).

Because the local and remote hash functions differed, `reconcile()` could never match SHAs, causing all tracked files to be treated as drifted and re-downloaded/overwritten on every run.

`sha1_file()` now computes the git blob object hash via a new helper `git_blob_sha1(data: bytes) -> str`, matching GitHub API / `git hash-object` semantics. The version marker was bumped from v3 to v3.1.

---

## 2. Exact Diff Applied

```diff
--- a/tools/sync_check.py
+++ b/tools/sync_check.py
@@ -37,1 +37,1 @@
-USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) sync_check/3.0"
+USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) sync_check/3.1"
@@ -95,12 +95,17 @@
-def sha1_bytes(data: bytes) -> str:
-    """Return SHA-1 hex digest of bytes."""
-    return hashlib.sha1(data).hexdigest()
+def git_blob_sha1(data: bytes) -> str:
+    """SHA-1 of a git blob object for `data` — matches the `sha`
+    field from GitHub's Tree API / `git hash-object` semantics:
+    SHA-1("blob " + len(data) + "\0" + data). NOT a plain content
+    hash — GitHub's API never returns one of those."""
+    header = f"blob {len(data)}\x00".encode()
+    return hashlib.sha1(header + data).hexdigest()

 def sha1_file(path: str) -> Optional[str]:
-    """Return SHA-1 hex digest of file contents, or None if unreadable."""
+    """Return the git blob SHA-1 of a file's contents, or None if
+    unreadable."""
     try:
         with open(path, "rb") as f:
-            return sha1_bytes(f.read())
+            return git_blob_sha1(f.read())
     except OSError:
         return None
@@ -363,1 +368,1 @@
-    log("=== Exhaustive Workspace Sync v3 ===\n")
+    log("=== Exhaustive Workspace Sync v3.1 ===\n")
```

---

## 3. Verification

### Step 1: Verification against `git hash-object`
Command:
```bash
python3 -c '
import subprocess
from tools.sync_check import sha1_file

test_files = ["AGENTS.md", "metadata.json", "app/build.gradle.kts"]
for path in test_files:
    git_hash = subprocess.check_output(["git", "hash-object", path]).decode().strip()
    python_hash = sha1_file(path)
    print(f"File: {path}")
    print(f"  git hash-object: {git_hash}")
    print(f"  sha1_file():     {python_hash}")
    print(f"  Match:           {git_hash == python_hash}")
    assert git_hash == python_hash
'
```

Output:
```text
File: AGENTS.md
  git hash-object: 6bb8a54f9701a755fdf1ea1b2160aac050e9e66a
  sha1_file():     6bb8a54f9701a755fdf1ea1b2160aac050e9e66a
  Match:           True
File: metadata.json
  git hash-object: 226d7833820cc80c7ff301585d91788404848521
  sha1_file():     226d7833820cc80c7ff301585d91788404848521
  Match:           True
File: app/build.gradle.kts
  git hash-object: 98e15e18bf4f3a680e0f7b3e0253b8fe281bb1bc
  sha1_file():     98e15e18bf4f3a680e0f7b3e0253b8fe281bb1bc
  Match:           True
```

### Step 2: Verification of `python3 tools/sync_check.py`
Command:
```bash
python3 tools/sync_check.py
```

Output:
```text
=== Exhaustive Workspace Sync v3.1 ===

[INFO] Detected default branch: main
[INFO] Remote: inscope-labs/abx-server @ main
[INFO] Base URL: https://raw.githubusercontent.com/inscope-labs/abx-server/main/
[INFO] Fetching remote file tree with SHAs...
[INFO] Remote tracks 334 files.
[INFO] Walking local filesystem...
[INFO] Local has 245 candidate files.
[ADDED] .env.example (new on upstream)
[ADDED] .github/workflows/build-aab-bundle.yml (new on upstream)
[ADDED] .github/workflows/build-apk-debug.yml (new on upstream)
[ADDED] .github/workflows/build-apk-release.yml (new on upstream)
[ADDED] .gitignore (new on upstream)
[ADDED] app/1024.png (new on upstream)
[ADDED] app/play_store_512.png (new on upstream)
[DRIFT] app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml (SHA mismatch: local=b3e26b4c... remote=90f95809...)
[FIXED] Overwrote app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml with remote content.
[ADDED] app/src/main/res/mipmap-hdpi/ic_launcher.png (new on upstream)
[ADDED] app/src/main/res/mipmap-hdpi/ic_launcher_adaptive_back.png (new on upstream)
[ADDED] app/src/main/res/mipmap-hdpi/ic_launcher_adaptive_fore.png (new on upstream)
[ADDED] app/src/main/res/mipmap-mdpi/ic_launcher.png (new on upstream)
[ADDED] app/src/main/res/mipmap-mdpi/ic_launcher_adaptive_back.png (new on upstream)
[ADDED] app/src/main/res/mipmap-mdpi/ic_launcher_adaptive_fore.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xhdpi/ic_launcher.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xhdpi/ic_launcher_adaptive_back.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xhdpi/ic_launcher_adaptive_fore.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xxhdpi/ic_launcher.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xxhdpi/ic_launcher_adaptive_back.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xxhdpi/ic_launcher_adaptive_fore.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xxxhdpi/ic_launcher.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xxxhdpi/ic_launcher_adaptive_back.png (new on upstream)
[ADDED] app/src/main/res/mipmap-xxxhdpi/ic_launcher_adaptive_fore.png (new on upstream)
[PRESERVED] debug.keystore (local-only, not in upstream)
[PRESERVED] debug.keystore.base64 (local-only, not in upstream)
[PRESERVED] env.example (local-only, not in upstream)
[PRESERVED] github/workflows/build-aab-bundle.yml (local-only, not in upstream)
[PRESERVED] github/workflows/build-apk-debug.yml (local-only, not in upstream)
[PRESERVED] github/workflows/build-apk-release.yml (local-only, not in upstream)
[PRESERVED] gitignore (local-only, not in upstream)

=== Sync Summary ===
Files checked for drift:              238
Unchanged (SHA match, skipped):       237
Drifted & overwritten:                1
Upstream additions fetched:           22
Upstream deletions applied locally:   0
Local-only files (preserved):         7
Fetch errors:                         0
Write errors:                         0
Delete errors:                        0
```

### Step 3: Syntax compilation check
Command:
```bash
python3 -m py_compile tools/sync_check.py
```
Output: Exit code 0 (no syntax errors).
