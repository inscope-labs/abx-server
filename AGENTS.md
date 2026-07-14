# Standing Instructions for AI Studio Build Agent — abx-server

## 1. GitHub Drift Protection

This workspace has no local git pull/fetch capability and cannot verify
it is in sync with GitHub `main`. Treat GitHub as the sole source of
truth for the following paths. Before editing any of them, fetch the
current live content from:

https://raw.githubusercontent.com/inscope-labs/abx-server/main/<path>

and compare it against your local working copy. If they differ, report
the diff and pause for confirmation before overwriting.

If the fetch fails for any reason (404, network error, timeout, or any
non-success response), do NOT assume the local files are correct and do
NOT proceed with the edit. Stop, report the exact URL you tried and the
error you got, and wait for explicit confirmation before making any
change to a protected path. A failed drift check is not the same as
"no drift" — never treat it as permission to proceed.

Protected paths:
- .github/workflows/*.yml
- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- settings.gradle.kts
- build.gradle.kts
- gradle.properties

Never assume the local workspace reflects the current state of `main`.

## 2. Mandatory Process Report on Every Task

This environment provides no way to copy, save, or download your
responses. You MUST record a report for every task you complete, saved
as an actual file in the repository (not just a chat response),
committed and pushed:

Path: agent-reports/<UTC-ISO-timestamp>-<short-task-slug>.md

The report must include:
- What was asked.
- What you actually changed (files touched, with a diff or summary).
- Any commands you ran and their results.
- Any assumptions you made.
- Any errors, partial failures, or things you were unable to verify.

Do not overwrite previous reports — each task gets its own timestamped
file. This folder must NOT be gitignored; it must be pushed to GitHub
so it can be read outside this environment.

## 3. Scope Discipline

Only make the changes explicitly requested in the current prompt. Do
not regenerate, reformat, or "improve" files beyond what was asked,
even if it seems in the spirit of the request.
