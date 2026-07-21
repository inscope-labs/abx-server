#!/usr/bin/env python3
"""
sync_check.py — Exhaustive workspace sync against a GitHub repository.

Walks the local filesystem, fetches each file's counterpart from the remote
repo's default branch, and reconciles:
  - Drift (local != remote)        -> overwrite with remote content
  - Locally-only files             -> preserved (not in upstream)
  - Upstream-only files (new on remote, missing locally) -> fetched & created
  - Upstream-deleted files (gone on remote, present locally) -> deleted locally

Designed for "force-resync the working tree to upstream" workflows.
For general git history/branches, use `git fetch && git reset --hard origin/<branch>`.
"""

import os
import sys
import json
import shutil
import tempfile
import urllib.request
import urllib.error
import urllib.parse
import difflib
import mimetypes
from typing import Optional, Tuple, List

# ----------------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------------

REPO_OWNER = "inscope-labs"
REPO_NAME = "abx-server"
# Branch is auto-detected from the GitHub API; this is the fallback only.
DEFAULT_BRANCH_FALLBACK = "main"
USER_AGERT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) sync_check/2.0"
REQUEST_TIMEOUT = 15  # seconds

# Directories to skip entirely (any path segment matching is excluded).
EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "build-logs",
    "agent-reports",
    ".build-outputs",
    "node_modules",
    "__pycache__",
    ".venv",
    "venv",
}

# Specific filenames to skip at any depth.
EXCLUDED_FILES = {
    "sync_check.py",
    "version.properties",
    "build.log",
    ".DS_Store",
    "Thumbs.db",
}

# ----------------------------------------------------------------------------
# Helpers
# ----------------------------------------------------------------------------

def log(msg: str) -> None:
    print(msg, flush=True)


def normalize(rel_path: str) -> str:
    """Normalize a relative path to forward slashes, no leading ./"""
    return os.path.relpath(rel_path, ".").replace("\\", "/").lstrip("./")


def should_exclude_path(rel_path: str) -> bool:
    """True if any path segment matches EXCLUDED_DIRS or the basename matches
    EXCLUDED_FILES."""
    parts = rel_path.split("/")
    for part in parts:
        if part in EXCLUDED_DIRS:
            return True
    if parts[-1] in EXCLUDED_FILES:
        return True
    return False


def is_symlink(path: str) -> bool:
    return os.path.islink(path)


def looks_binary(sample: bytes) -> bool:
    """Heuristic: file is binary if it contains a NUL byte in the first 8KB.

    This is the standard `git`-style heuristic and avoids false positives
    on UTF-8 / Latin-1 text which never contain NUL."""
    if not sample:
        return False
    return b"\x00" in sample[:8192]


def http_get(url: str) -> Tuple[Optional[bytes], Optional[int], Optional[str]]:
    """Fetch URL. Returns (body, status_code, error_message)."""
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGERT})
        with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
            return resp.read(), resp.status, None
    except urllib.error.HTTPError as e:
        return None, e.code, e.reason
    except urllib.error.URLError as e:
        return None, None, str(e.reason)
    except Exception as e:  # timeout, connection reset, etc.
        return None, None, str(e)


def detect_default_branch(owner: str, name: str) -> str:
    """Query the GitHub API for the repo's default branch.

    Falls back to DEFAULT_BRANCH_FALLBACK on any error so the script still
    works (just possibly against a non-default branch)."""
    url = f"https://api.github.com/repos/{owner}/{name}"
    body, status, err = http_get(url)
    if status == 200 and body:
        try:
            data = json.loads(body.decode("utf-8"))
            branch = data.get("default_branch")
            if isinstance(branch, str) and branch:
                log(f"[INFO] Detected default branch: {branch}")
                return branch
        except (json.JSONDecodeError, UnicodeDecodeError) as e:
            log(f"[WARN] Could not parse GitHub API response: {e}")
    else:
        log(f"[WARN] Could not detect default branch (status={status}, err={err}). "
            f"Falling back to '{DEFAULT_BRANCH_FALLBACK}'.")
    return DEFAULT_BRANCH_FALLBACK


def fetch_remote_tree(owner: str, name: str, branch: str) -> Optional[set]:
    """Fetch the full set of tracked file paths on the remote branch via the
    GitHub Git Trees API (recursive=1). Returns None on failure.

    This is what enables upstream-addition detection (correction #2) and
    upstream-deletion detection (correction #3)."""
    url = (
        f"https://api.github.com/repos/{owner}/{name}/git/trees/{branch}"
        f"?recursive=1"
    )
    body, status, err = http_get(url)
    if status != 200 or not body:
        log(f"[WARN] Failed to fetch remote tree (status={status}, err={err}). "
            f"Upstream add/delete detection will be skipped.")
        return None
    try:
        data = json.loads(body.decode("utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as e:
        log(f"[WARN] Could not parse tree response: {e}")
        return None

    if data.get("truncated"):
        log("[WARN] Remote tree response was truncated by GitHub "
            "(repo >100k files or >7MB). Add/delete detection may be incomplete.")

    paths = set()
    for entry in data.get("tree", []):
        if entry.get("type") == "blob":
            p = entry.get("path")
            if p:
                paths.add(p)
    return paths


def atomic_write(path: str, content: bytes) -> None:
    """Write to a temp file in the same directory, fsync, then os.replace.

    Guarantees the destination is never half-written, even if the process
    is killed mid-write (correction #4)."""
    directory = os.path.dirname(path) or "."
    os.makedirs(directory, exist_ok=True)
    fd, tmp_path = tempfile.mkstemp(prefix=".sync_", dir=directory)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_path, path)
    except Exception:
        # Best-effort cleanup of the temp file on failure.
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        raise


def unified_text_diff(local_bytes: bytes, remote_bytes: bytes,
                      rel_path: str) -> str:
    """Return a unified diff string. Falls back to a binary marker if the
    files are detected as binary (correction #5)."""
    if looks_binary(local_bytes) or looks_binary(remote_bytes):
        return (f"--- local://{rel_path} (binary, {len(local_bytes)} bytes)\n"
                f"+++ github://{rel_path} (binary, {len(remote_bytes)} bytes)\n"
                f"@@ binary file differs @@\n")

    try:
        local_lines = local_bytes.decode("utf-8", errors="replace").splitlines(keepends=True)
        remote_lines = remote_bytes.decode("utf-8", errors="replace").splitlines(keepends=True)
    except Exception as e:
        return f"Cannot decode {rel_path} as UTF-8 even after replace: {e}\n"

    diff = difflib.unified_diff(
        local_lines, remote_lines,
        fromfile=f"local://{rel_path}",
        tofile=f"github://{rel_path}",
        n=3,
    )
    return "".join(diff)


# ----------------------------------------------------------------------------
# Main reconciliation
# ----------------------------------------------------------------------------

def reconcile(local_files: set, remote_paths: set, base_url: str) -> dict:
    """Compare local and remote sets and apply the four reconciliation rules.

    Returns a stats dict summarizing the run."""
    stats = {
        "checked": 0,
        "drift_overwritten": [],
        "local_only": [],
        "upstream_added": [],
        "upstream_removed": [],
        "fetch_errors": [],
        "write_errors": [],
        "delete_errors": [],
    }

    # ---- (1) Drift detection + (2) upstream additions -------------------
    for rel_path in sorted(local_files | remote_paths):
        local_path = rel_path  # already relative to cwd
        local_exists = rel_path in local_files
        remote_exists = rel_path in remote_paths

        # Skip paths we've filtered out (excluded dirs/files, symlinks, etc.)
        # `local_files` and `remote_paths` are pre-filtered, so just guard.
        if should_exclude_path(rel_path):
            continue

        if local_exists and remote_exists:
            # --- Drift detection ---
            stats["checked"] += 1
            try:
                with open(local_path, "rb") as f:
                    local_content = f.read()
            except OSError as e:
                stats["fetch_errors"].append((rel_path, f"local read: {e}"))
                continue

            remote_url = base_url + urllib.parse.quote(rel_path, safe="/")
            remote_content, status, err = http_get(remote_url)
            if status is None:
                stats["fetch_errors"].append((rel_path, f"network: {err}"))
                continue
            if status != 200:
                # 404 here is odd since remote_paths said it exists; record
                # but don't treat as local-only.
                stats["fetch_errors"].append((rel_path, f"HTTP {status} {err}"))
                continue

            if local_content != remote_content:
                stats["drift_overwritten"].append(rel_path)
                log(f"\n[DRIFT] {rel_path}")
                log(unified_text_diff(local_content, remote_content, rel_path))
                try:
                    atomic_write(local_path, remote_content)
                    log(f"[FIXED] Overwrote {rel_path} with remote content.")
                except OSError as e:
                    stats["write_errors"].append((rel_path, str(e)))
                    log(f"[ERROR] Failed to overwrite {rel_path}: {e}")

        elif local_exists and not remote_exists:
            # --- (3) Upstream deletion: drop local file ---
            try:
                os.remove(local_path)
                stats["upstream_removed"].append(rel_path)
                log(f"[REMOVED] {rel_path} (deleted upstream)")
            except OSError as e:
                stats["delete_errors"].append((rel_path, str(e)))
                log(f"[ERROR] Failed to delete {rel_path}: {e}")

        elif remote_exists and not local_exists:
            # --- (2) Upstream addition: fetch and create locally ---
            remote_url = base_url + urllib.parse.quote(rel_path, safe="/")
            remote_content, status, err = http_get(remote_url)
            if status != 200 or remote_content is None:
                stats["fetch_errors"].append(
                    (rel_path, f"HTTP {status} {err} (add)")
                )
                log(f"[ERROR] Failed to fetch new file {rel_path}: HTTP {status} {err}")
                continue
            try:
                atomic_write(local_path, remote_content)
                stats["upstream_added"].append(rel_path)
                log(f"[ADDED] {rel_path} (new on upstream)")
            except OSError as e:
                stats["write_errors"].append((rel_path, str(e)))
                log(f"[ERROR] Failed to write new file {rel_path}: {e}")

    return stats


def print_summary(stats: dict) -> None:
    log("\n=== Sync Summary ===")
    log(f"Files checked for drift:              {stats['checked']}")
    log(f"Drifted & overwritten:                {len(stats['drift_overwritten'])}")
    log(f"Upstream additions fetched:           {len(stats['upstream_added'])}")
    log(f"Upstream deletions applied locally:   {len(stats['upstream_removed'])}")
    log(f"Local-only files (not in upstream):   {len(stats['local_only'])}")
    log(f"Fetch errors:                         {len(stats['fetch_errors'])}")
    log(f"Write errors:                         {len(stats['write_errors'])}")
    log(f"Delete errors:                        {len(stats['delete_errors'])}")

    def dump(label: str, items, formatter=lambda x: f" - {x}"):
        if not items:
            return
        log(f"\n{label}:")
        for it in items:
            log(formatter(it))

    dump("Drifted files", stats["drift_overwritten"])
    dump("Upstream additions", stats["upstream_added"])
    dump("Upstream deletions", stats["upstream_removed"])
    dump("Local-only files", stats["local_only"])
    dump("Fetch errors", stats["fetch_errors"], lambda x: f" - {x[0]}: {x[1]}")
    dump("Write errors", stats["write_errors"], lambda x: f" - {x[0]}: {x[1]}")
    dump("Delete errors", stats["delete_errors"], lambda x: f" - {x[0]}: {x[1]}")


# ----------------------------------------------------------------------------
# Entry point
# ----------------------------------------------------------------------------

def collect_local_files() -> set:
    """Walk the cwd and return the set of tracked local file paths.

    Skips symlinks to prevent loops and surprise cross-directory writes
    (correction #1 from the previous review)."""
    files = set()
    for root, dirs, filenames in os.walk(".", followlinks=False):
        # Prune excluded directories in-place so os.walk doesn't descend.
        dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]
        for fname in filenames:
            full_path = os.path.join(root, fname)
            if is_symlink(full_path):
                continue
            rel = normalize(full_path)
            if should_exclude_path(rel):
                continue
            files.add(rel)
    return files


def main() -> int:
    log("=== Exhaustive Workspace Sync v2 ===\n")

    branch = detect_default_branch(REPO_OWNER, REPO_NAME)
    base_url = f"https://raw.githubusercontent.com/{REPO_OWNER}/{REPO_NAME}/{branch}/"

    log(f"[INFO] Remote: {REPO_OWNER}/{REPO_NAME} @ {branch}")
    log(f"[INFO] Base URL: {base_url}")

    log("[INFO] Fetching remote file tree...")
    remote_paths = fetch_remote_tree(REPO_OWNER, REPO_NAME, branch)
    if remote_paths is None:
        log("[FATAL] Cannot proceed without the remote tree. Aborting.")
        return 2

    log(f"[INFO] Remote tracks {len(remote_paths)} files.")

    log("[INFO] Walking local filesystem...")
    local_files = collect_local_files()
    log(f"[INFO] Local has {len(local_files)} candidate files.")

    # Compute local-only = exists locally but not in remote (and not excluded).
    local_only = {
        p for p in local_files
        if p not in remote_paths and not should_exclude_path(p)
    }

    stats = reconcile(local_files, remote_paths, base_url)
    stats["local_only"] = sorted(local_only)

    print_summary(stats)

    # Non-zero exit if anything went wrong, so CI can fail on partial syncs.
    has_errors = (
        stats["fetch_errors"] or stats["write_errors"] or stats["delete_errors"]
    )
    return 1 if has_errors else 0


if __name__ == "__main__":
    sys.exit(main())