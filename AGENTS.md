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

## 1a. CI-Owned Paths — Never Edit, Never Stage, Never Delete

The following paths are exclusively written by GitHub Actions
workflows, never by this agent, regardless of what a task asks for:

- version.properties
- build-logs/**

Never open, edit, or stage these paths for any reason, even
incidentally. Your local workspace copy of these paths (if any) is
guaranteed to be stale — GitHub Actions updates them on every workflow
run in ways this workspace never observes, since it cannot pull.

## 1b. Never Stage or Commit the Entire Working Tree

NEVER use `git add -A`, `git add .`, `git add --all`, or any
equivalent blanket-staging command. Every commit must stage ONLY the
exact files the current task explicitly asked you to create or
modify — nothing else, added by explicit path, one at a time or as an
explicit list.

Before every commit, run `git status` and verify the staged file list
contains ONLY the files this task was asked to touch. If
version.properties, anything under build-logs/, or any other
unexpected file appears as staged (whether as a modification or a
deletion), unstage it immediately (`git restore --staged <path>`)
before committing. A commit that would delete or modify a file outside
the current task's explicit scope must never be pushed.

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

## 4. Design Token Discipline

All spacing, padding, and gap values in Compose UI code must come from
`Spacing` (app/src/main/java/com/inscopelabs/abx/server/ui/theme/Spacing.kt).
All icon/avatar container sizes must come from `IconSize` in the same
file. Never write a raw `.dp` literal for spacing or icon sizing in any
Composable — if the value you need doesn't exist in Spacing/IconSize,
stop and ask, don't invent one.

Never write a hardcoded `Color(0xFF......)` literal for anything that
represents status, state, or theme-dependent color. Use
`MaterialTheme.colorScheme.*` or `MaterialTheme.abxStatusColors.*`
(ui/theme/Theme.kt) instead — hardcoded hex breaks dark mode and drifts
the UI away from the app's single palette over time.

The primary/accent color (blue) is reserved for things that are
genuinely active, selected, or the one primary action on a screen. Do
not apply it decoratively to icon containers, cards, or chips by
default — default to `MaterialTheme.colorScheme.surfaceVariant` /
`onSurfaceVariant` for neutral elements. If in doubt, ask before adding
a new use of the primary color.

For any new list of similar items (rows with an icon, title, optional
subtitle, and optional trailing content), use the existing
`ABXListRow` composable (ui/Components.kt) rather than building a new
bespoke Row from scratch. If ABXListRow genuinely doesn't fit a new
use case, extend it rather than duplicating its structure elsewhere.

This rule exists because a prior audit found 18 distinct ad-hoc dp
values and multiple hardcoded hex colors accumulated across a single
file, entirely from individually-reasonable changes made without a
shared scale. Treat this section with the same weight as the
CI-owned-paths and scope-discipline rules above it — this is not a
style suggestion, it's a standing constraint.

