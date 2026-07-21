package com.inscopelabs.abx.server.ui

// DESIGN TOKEN DISCIPLINE — see AGENTS.md section 4 before editing.
// Spacing/sizing: use Spacing.* and IconSize.* (ui/theme/Spacing.kt).
// Never write a raw .dp literal for padding, gaps, or icon sizing.
// Color: use MaterialTheme.colorScheme.* or MaterialTheme.abxStatusColors.*.
// Never write a hardcoded Color(0xFF......) literal.
// Primary/accent blue = active/selected/primary-action only, never decorative.

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.inscopelabs.abx.server.R
import com.inscopelabs.abx.server.BuildConfig
import com.inscopelabs.abx.server.ui.theme.abxStatusColors
import com.inscopelabs.abx.server.ui.theme.Spacing
import com.inscopelabs.abx.server.ui.theme.IconSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class StatusSeverity {
    SUCCESS, WARNING, ERROR, INFO
}

@Composable
fun ABXCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = CardDefaults.outlinedCardBorder(),
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { onClick() },
            border = border,
            colors = colors,
            shape = shape,
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            border = border,
            colors = colors,
            shape = shape,
            content = content
        )
    }
}

/**
 * Status chip. Previously used hardcoded hex per severity (Color(0xFFE8F5E9)
 * etc.) — now routes through MaterialTheme's error role and the app's own
 * abxStatusColors (success/warning), so it adapts to dark mode and stays
 * consistent with every other status indicator in the app instead of being
 * its own disconnected palette.
 */
@Composable
fun ABXStatusChip(
    status: String,
    modifier: Modifier = Modifier,
    severity: StatusSeverity = StatusSeverity.INFO
) {
    val containerColor = when (severity) {
        StatusSeverity.SUCCESS -> MaterialTheme.abxStatusColors.successContainer
        StatusSeverity.WARNING -> MaterialTheme.abxStatusColors.warningContainer
        StatusSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
        StatusSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (severity) {
        StatusSeverity.SUCCESS -> MaterialTheme.abxStatusColors.success
        StatusSeverity.WARNING -> MaterialTheme.abxStatusColors.warning
        StatusSeverity.ERROR -> MaterialTheme.colorScheme.error
        StatusSeverity.INFO -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(Spacing.xs),
        modifier = modifier
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

/**
 * Metric card. Icon container is neutral gray by default now, not always
 * blue — a metric isn't "active" or "primary" just because it has a number
 * next to it. Pass accentIcon = true for the rare case a specific metric
 * genuinely represents an active/highlighted state.
 */
@Composable
fun ABXMetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accentIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val iconContainerColor = if (accentIcon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val iconTint = if (accentIcon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    ABXCard(
        modifier = modifier,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(IconSize.xl)
                        .clip(RoundedCornerShape(Spacing.md))
                        .background(iconContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(IconSize.sm + Spacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
fun ABXServiceTile(
    title: String,
    status: String,
    isActive: Boolean,
    icon: ImageVector,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "service_tile_status_color"
    )

    ABXCard(
        modifier = modifier,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(IconSize.lg)
                        .clip(RoundedCornerShape(Spacing.sm + Spacing.xs))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(IconSize.sm)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("service_switch_${title.replace(" ", "_").lowercase()}")
            )
        }
    }
}

@Composable
fun ABXQuickActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(Spacing.md),
        modifier = modifier
            .width(130.dp)
            .height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize.sm + Spacing.xs)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Shared row for any list of similar items — tools, connected clients,
 * access entries. One row rhythm used everywhere instead of each screen
 * building its own Row(icon, title, subtitle, trailing) from scratch.
 */
@Composable
fun ABXListRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ABXCard(
        onClick = onClick,
        modifier = modifier.padding(vertical = Spacing.xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(IconSize.lg)
                        .clip(RoundedCornerShape(Spacing.sm + Spacing.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.sm)
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.md))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(IconSize.sm)
                )
            }
        }
    }
}

@Composable
fun ABXEmptyState(
    message: String,
    icon: ImageVector = Icons.Default.Inbox,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.xxl)
                .clip(RoundedCornerShape(IconSize.xxl / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(IconSize.sm + Spacing.md)
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Log viewer. Previously a hardcoded dark terminal panel (Color(0xFF1E1E1E)
 * + hardcoded green/red/gray) sitting inside an otherwise light Material
 * app — its own disconnected visual language. Now uses the theme's own
 * surface/error/success roles, still monospace (appropriate for log
 * content) but no longer a jarring "hacker console inside a corporate
 * card" clash, and it adapts correctly in dark mode.
 */
@Composable
fun ABXLogViewer(
    logs: List<JSONObject>,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(Spacing.md))
            .padding(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "System console logs",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onClear != null) {
                TextButton(onClick = onClear) {
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        if (logs.isEmpty()) {
            Text(
                text = "No active logs recorded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(Spacing.sm)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(logs.reversed()) { log ->
                    val timestamp = log.optLong("timestamp", System.currentTimeMillis())
                    val details = log.optString("details", "")
                    val reason = log.optString("reasonCode", "INFO")
                    val dateStr = try {
                        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                        sdf.format(Date(timestamp))
                    } catch (e: Exception) {
                        "00:00:00"
                    }
                    val isAlert = reason.contains("EXPIRED") || reason.contains("REJECTION") || reason.contains("OUT_OF_BOUNDS")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = "[$dateStr]",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "[$reason]",
                            fontFamily = FontFamily.Monospace,
                            color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.abxStatusColors.success,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = details,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ABXConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        },
        shape = RoundedCornerShape(Spacing.xl)
    )
}

/**
 * Compact top bar: fixed 48dp height (vs. Material3's default 64dp TopAppBar).
 * Branding uses a circular avatar-style mark rather than a full title lockup,
 * and secondary actions collapse into a single overflow menu instead of a
 * row of icon buttons — mirrors the reference layout: mark + name, one
 * overflow affordance, done.
 */
@Composable
fun CompactTopBar(
    appName: String,
    isSessionActive: Boolean,
    onAboutClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onDeleteDataClick: () -> Unit,
    onDiagnosticsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(IconSize.md)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.sm - Spacing.xs)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = appName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isSessionActive) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.abxStatusColors.success)
                )
            }
        }

        Box {
            IconButton(
                onClick = { overflowExpanded = true },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("top_bar_about_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.btn_about_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.sm - Spacing.xs)
                )
            }

            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_about)) },
                    onClick = {
                        overflowExpanded = false
                        onAboutClick()
                    },
                    modifier = Modifier.testTag("overflow_menu_about")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_privacy_policy)) },
                    onClick = {
                        overflowExpanded = false
                        onPrivacyPolicyClick()
                    },
                    modifier = Modifier.testTag("overflow_menu_privacy_policy")
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_delete_data)) },
                    onClick = {
                        overflowExpanded = false
                        onDeleteDataClick()
                    },
                    modifier = Modifier.testTag("overflow_menu_delete_data")
                )
                if (BuildConfig.DEBUG) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_diagnostics)) },
                        onClick = {
                            overflowExpanded = false
                            onDiagnosticsClick()
                        },
                        modifier = Modifier.testTag("overflow_menu_diagnostics")
                    )
                }
            }
        }
    }
}

/**
 * Secondary contextual toolbar. Holds the actions that don't deserve a
 * permanent slot in the bottom nav (rotate key, pairing, local bridge) plus
 * quick jumps to Access and Remove — the two destinations dropped from the
 * bottom bar when it was trimmed to three items. This is what "takes the
 * load off" the top bar and the dashboard's own quick-action clutter.
 */
@Composable
fun ContextToolbar(
    onRotateKey: () -> Unit,
    onShowPairing: () -> Unit,
    onOpenLocalBridge: () -> Unit,
    onNavigateToAccess: () -> Unit,
    onNavigateToRemove: () -> Unit,
    onNavigateToActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        ToolbarAction(
            icon = Icons.Default.VpnKey,
            label = stringResource(R.string.tab_access),
            onClick = onNavigateToAccess,
            testTag = "toolbar_access"
        )
        ToolbarAction(
            icon = Icons.Default.History,
            label = stringResource(R.string.tab_activity),
            onClick = onNavigateToActivity,
            testTag = "toolbar_activity"
        )
        ToolbarAction(
            icon = Icons.Default.QrCodeScanner,
            label = stringResource(R.string.btn_pairing),
            onClick = onShowPairing,
            testTag = "toolbar_pairing"
        )
        ToolbarAction(
            icon = Icons.Default.Refresh,
            label = stringResource(R.string.btn_rotate_key),
            onClick = onRotateKey,
            testTag = "toolbar_rotate_key"
        )
        ToolbarAction(
            icon = Icons.Default.Link,
            label = stringResource(R.string.btn_local_bridge),
            onClick = onOpenLocalBridge,
            testTag = "toolbar_local_bridge"
        )
        ToolbarAction(
            icon = Icons.Default.Delete,
            label = stringResource(R.string.tab_remove),
            onClick = onNavigateToRemove,
            testTag = "toolbar_remove"
        )
    }
}

@Composable
private fun ToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Spacing.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md - Spacing.xs, vertical = Spacing.xs + 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.sm - Spacing.xs)
        )
        Spacer(modifier = Modifier.width(Spacing.xs + 2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
