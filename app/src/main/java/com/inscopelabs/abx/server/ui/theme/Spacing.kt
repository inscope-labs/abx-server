package com.inscopelabs.abx.server.ui.theme

// DESIGN TOKEN DISCIPLINE — see AGENTS.md section 4 before editing.
// Spacing/sizing: use Spacing.* and IconSize.* (ui/theme/Spacing.kt).
// Never write a raw .dp literal for padding, gaps, or icon sizing.
// Color: use MaterialTheme.colorScheme.* or MaterialTheme.abxStatusColors.*.
// Never write a hardcoded Color(0xFF......) literal.
// Primary/accent blue = active/selected/primary-action only, never decorative.

import androidx.compose.ui.unit.dp

/**
 * The app's entire spacing scale. Six values, nothing else — every padding,
 * gap, and margin in the UI should resolve to one of these rather than a
 * one-off dp literal. This is what a calm/Heroku-console-style UI actually
 * runs on: not a rule about whitespace amount, but a rule that whitespace
 * only ever comes from a fixed, small vocabulary. A previous audit of
 * Components.kt found 18 distinct ad-hoc dp values in one file — that's
 * the drift this scale exists to prevent.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * Sizes for icon containers, avatars, and similar square/circular elements.
 * Kept separate from Spacing because these describe component dimensions,
 * not gaps between things — but same principle: a fixed small vocabulary,
 * not whatever number looked right in the moment.
 */
object IconSize {
    val sm = 20.dp   // inline icon, no container
    val md = 28.dp   // small icon container (top bar avatar mark)
    val lg = 40.dp   // list row leading icon container
    val xl = 48.dp   // metric card icon container
    val xxl = 64.dp  // empty-state icon container
}
