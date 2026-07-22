# Process Report: Merge Dashboard into WorkspaceFragment (Stage 2)

**Date:** 2026-07-22
**Task:** Stage 2 of 3 — Merge Dashboard into WorkspaceFragment as a "Home" tab alongside "Chat", delete orphaned DashboardFragment, and apply UI/cleanups.

---

## 1. Request Overview
The user requested Stage 2 refactoring to merge the standalone Dashboard content into `WorkspaceFragment`:
1. Delete `DashboardFragment.kt` and `fragment_dashboard.xml` (orphaned since Stage 1).
2. Modify `fragment_workspace.xml` to add a `TabLayout` (`workspaceTabLayout`) with "Home" and "Chat" tabs directly below `chatToolbar`.
3. Port the complete Dashboard layout into `containerHomeTab` (System Health Panel, Session Metric & Fingerprint Cards, Quick Actions, AI Workspace Summary).
4. Wrap the existing Chat layout in `containerChatTab`.
5. Update `WorkspaceFragment.kt` to port in all Dashboard state management, session lifecycle tracking, TEE/StrongBox hardware security checks, audit log verification, and tab visibility toggling.
6. Hide Chat-specific action buttons (`chatNewSessionButton`, `chatSettingsButton`) when the Home tab is selected, and show them when the Chat tab is selected.
7. Perform required cleanups:
   - Add `@dimen/switch_height` (44dp) to `dimens.xml`.
   - Update `fragment_workspace.xml` and `fragment_server.xml` to reference `@dimen/switch_height`.
   - Simplify `MainActivity.switchTopLevelWorkspace()` by removing the redundant `TOOLBOX` branch check.

---

## 2. Changes Made

### Files Deleted
- `app/src/main/java/com/inscopelabs/abx/server/DashboardFragment.kt`
- `app/src/main/res/layout/fragment_dashboard.xml`

### Files Modified
- `app/src/main/res/values/strings.xml`: Added `tab_home` ("Home") and `tab_chat` ("Chat").
- `app/src/main/res/values/dimens.xml`: Added `<dimen name="switch_height">44dp</dimen>`.
- `app/src/main/res/layout/fragment_server.xml`: Updated `workspaceServerSwitch` `android:layout_height` to `@dimen/switch_height`.
- `app/src/main/res/layout/fragment_workspace.xml`: Added `workspaceTabLayout`, `containerHomeTab` (Dashboard cards), and `containerChatTab` (Chat UI). Updated bottom switch height to `@dimen/switch_height`.
- `app/src/main/java/com/inscopelabs/abx/server/WorkspaceFragment.kt`:
  - Ported key material, SessionManager, KeyStoreManager, AuditLog, and TEE/StrongBox hardware status logic.
  - Implemented `setupTabs()` for switching between Home and Chat tabs and toggling action button visibilities.
  - Implemented `bindHomeViews()`, `observeSessionState()`, `startTimer()`, `toggleSession()`, `loadKeyMaterial()`, and `refreshHomeTab()`.
- `app/src/main/java/com/inscopelabs/abx/server/MainActivity.kt`: Simplified `switchTopLevelWorkspace(target: Workspace)` to directly invoke `showWorkspace(target)`.

---

## 3. Verification & Build Results
- Executed `compile_applet`.
- Build succeeded with zero errors (`Build succeeded - the applet is compiled`).
- No protected CI paths (`.github/workflows/*.yml`, `app/build.gradle.kts`, `AndroidManifest.xml`, etc.) were edited or modified.

---

## 4. Assumptions & Notes
- Default active tab in `WorkspaceFragment` is "Home" (position 0).
- Quick action navigation buttons on the Home tab correctly invoke `SecureNavigation.openSecureTab()` for ACCESS, REMOVE, and ACTIVITY tabs on ServerFragment.
