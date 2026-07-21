#!/usr/bin/env python3
"""
sync_check.py — Exhaustive workspace sync against a GitHub repository.

Walks the local filesystem, fetches each file's counterpart from the remote
repo's default branch, and reconciles:
  - Drift (local != remote)        -> overwrite with remote content
  - Locally-only files             -> PRESERVED (not deleted)
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
import hashlib
import time
import mimetypes
from typing import Optional, Tuple, List, Dict

# ----------------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------------

REPO_OWNER = "inscope-labs"
REPO_NAME = "abx-server"
# Branch is auto-detected from the GitHub API; this is the fallback only.
DEFAULT_BRANCH_FALLBACK = "main"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) sync_check/3.0"
REQUEST_TIMEOUT = 15  # seconds
MAX_RETRIES = 3
RETRY_BACKOFF_BASE = 2  # seconds

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


def sha1_bytes(data: bytes) -> str:
    """Return SHA-1 hex digest of bytes."""
    return hashlib.sha1(data).hexdigest()


def sha1_file(path: str) -> Optional[str]:
    """Return SHA-1 hex digest of file contents, or None if unreadable."""
    try:
        with open(path, "rb") as f:
            return sha1_bytes(f.read())
    except OSError:
        return None


def http_get(url: str, retries: int = MAX_RETRIES) -> Tuple[Optional[bytes], Optional[int], Optional[str]]:
    """Fetch URL with exponential backoff retry. Returns (body, status_code, error_message)."""
    last_error = None
    for attempt in range(retries + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT) as resp:
                return resp.read(), resp.status, None
        except urllib.error.HTTPError as e:
            # Retry on 5xx server errors and 429 rate limit
            if e.code in (429, 500, 502, 503, 504) and attempt < retries:
                wait = RETRY_BACKOFF_BASE * (2 ** attempt)
                log(f"[RETRY] HTTP {e.code} for {url}, waiting {wait}s... (attempt {attempt + 1}/{retries})")
                time.sleep(wait)
                continue
            return None, e.code, e.reason
        except urllib.error.URLError as e:
            if attempt < retries:
                wait = RETRY_BACKOFF_BASE * (2 ** attempt)
                log(f"[RETRY] URL error for {url}: {e.reason}, waiting {wait}s... (attempt {attempt + 1}/{retries})")
                time.sleep(wait)
                continue
            return None, None, str(e.reason)
        except Exception as e:
            if attempt < retries:
                wait = RETRY_BACKOFF_BASE * (2 ** attempt)
                log(f"[RETRY] Exception for {url}: {e}, waiting {wait}s... (attempt {attempt + 1}/{retries})")
                time.sleep(wait)
                continue
            return None, None, str(e)
    return None, None, last_error


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


def fetch_remote_tree(owner: str, name: str, branch: str) -> Optional[Dict[str, str]]:
    """Fetch the remote file tree mapping path -> SHA via the GitHub Git Trees API.

    Returns a dict mapping file paths to their blob SHAs, or None on failure.
    This enables SHA-based comparison to avoid downloading unchanged files."""
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

    path_to_sha = {}
    for entry in data.get("tree", []):
        if entry.get("type") == "blob":
            p = entry.get("path")
            sha = entry.get("sha")
            if p and sha:
                path_to_sha[p] = sha
    return path_to_sha


def atomic_write(path: str, content: bytes) -> None:
    """Write to a temp file in the same directory, fsync, then os.replace.

    Guarantees the destination is never half-written, even if the process
    is killed mid-write."""
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


# ----------------------------------------------------------------------------
# Main reconciliation
# ----------------------------------------------------------------------------

def reconcile(local_files: set, remote_shas: Dict[str, str], base_url: str) -> dict:
    """Compare local and remote sets and apply the four reconciliation rules.

    Uses SHA comparison to avoid downloading files that haven't changed.
    Returns a stats dict summarizing the run."""
    stats = {
        "checked": 0,
        "unchanged": 0,
        "drift_overwritten": [],
        "local_only": [],
        "upstream_added": [],
        "upstream_removed": [],
        "fetch_errors": [],
        "write_errors": [],
        "delete_errors": [],
    }

    # ---- (1) Drift detection + (2) upstream additions -------------------
    for rel_path in sorted(local_files | set(remote_shas.keys())):
        local_path = rel_path  # already relative to cwd
        local_exists = rel_path in local_files
        remote_exists = rel_path in remote_shas

        # Skip paths we've filtered out
        if should_exclude_path(rel_path):
            continue

        if local_exists and remote_exists:
            # --- Drift detection via SHA comparison ---
            stats["checked"] += 1
            local_sha = sha1_file(local_path)
            if local_sha is None:
                stats["fetch_errors"].append((rel_path, "local read failed"))
                continue

            remote_sha = remote_shas[rel_path]

            if local_sha == remote_sha:
                stats["unchanged"] += 1
                continue  # Skip download — file is identical

            # SHAs differ — need to download and overwrite
            remote_url = base_url + urllib.parse.quote(rel_path, safe="/")
            remote_content, status, err = http_get(remote_url)
            if status is None:
                stats["fetch_errors"].append((rel_path, f"network: {err}"))
                continue
            if status != 200:
                stats["fetch_errors"].append((rel_path, f"HTTP {status} {err}"))
                continue

            stats["drift_overwritten"].append(rel_path)
            log(f"[DRIFT] {rel_path} (SHA mismatch: local={local_sha[:8]}... remote={remote_sha[:8]}...)")
            try:
                atomic_write(local_path, remote_content)
                log(f"[FIXED] Overwrote {rel_path} with remote content.")
            except OSError as e:
                stats["write_errors"].append((rel_path, str(e)))
                log(f"[ERROR] Failed to overwrite {rel_path}: {e}")

        elif local_exists and not remote_exists:
            # --- (3) Local-only file: PRESERVE (do NOT delete) ---
            stats["local_only"].append(rel_path)
            log(f"[PRESERVED] {rel_path} (local-only, not in upstream)")

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
    log(f"Unchanged (SHA match, skipped):       {stats['unchanged']}")
    log(f"Drifted & overwritten:                {len(stats['drift_overwritten'])}")
    log(f"Upstream additions fetched:           {len(stats['upstream_added'])}")
    log(f"Upstream deletions applied locally:   {len(stats['upstream_removed'])}")
    log(f"Local-only files (preserved):         {len(stats['local_only'])}")
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
    dump("Local-only files (preserved)", stats["local_only"])
    dump("Fetch errors", stats["fetch_errors"], lambda x: f" - {x[0]}: {x[1]}")
    dump("Write errors", stats["write_errors"], lambda x: f" - {x[0]}: {x[1]}")
    dump("Delete errors", stats["delete_errors"], lambda x: f" - {x[0]}: {x[1]}")


# ----------------------------------------------------------------------------
# Entry point
# ----------------------------------------------------------------------------

def collect_local_files() -> set:
    """Walk the cwd and return the set of tracked local file paths.

    Skips symlinks to prevent loops and surprise cross-directory writes."""
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
    log("=== Exhaustive Workspace Sync v3 ===\n")

    branch = detect_default_branch(REPO_OWNER, REPO_NAME)
    base_url = f"https://raw.githubusercontent.com/{REPO_OWNER}/{REPO_NAME}/{branch}/"

    log(f"[INFO] Remote: {REPO_OWNER}/{REPO_NAME} @ {branch}")
    log(f"[INFO] Base URL: {base_url}")

    log("[INFO] Fetching remote file tree with SHAs...")
    remote_shas = fetch_remote_tree(REPO_OWNER, REPO_NAME, branch)
    if remote_shas is None:
        log("[FATAL] Cannot proceed without the remote tree. Aborting.")
        return 2

    log(f"[INFO] Remote tracks {len(remote_shas)} files.")

    log("[INFO] Walking local filesystem...")
    local_files = collect_local_files()
    log(f"[INFO] Local has {len(local_files)} candidate files.")

    stats = reconcile(local_files, remote_shas, base_url)

    print_summary(stats)

    # Non-zero exit if anything went wrong, so CI can fail on partial syncs.
    has_errors = (
        stats["fetch_errors"] or stats["write_errors"] or stats["delete_errors"]
    )
    return 1 if has_errors else 0


if __name__ == "__main__":
    sys.exit(main())