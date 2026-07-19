package com.inscopelabs.abx.server.toolbox

// DESIGN TOKEN DISCIPLINE — see AGENTS.md section 4 before editing.
// Spacing/sizing: use Spacing.* and IconSize.* (ui/theme/Spacing.kt).
// Never write a raw .dp literal for padding, gaps, or icon sizing.
// Color: use MaterialTheme.colorScheme.* or MaterialTheme.abxStatusColors.*.
// Never write a hardcoded Color(0xFF......) literal.
// Primary/accent blue = active/selected/primary-action only, never decorative.

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.inscopelabs.abx.server.ui.ABXListRow
import com.inscopelabs.abx.server.ui.theme.Spacing
import com.inscopelabs.abx.server.ui.theme.abxStatusColors

/**
 * Toolbox: categorized JS tools that run inside a sandboxed WebView, bridged
 * to the app's own MCP execution pipeline via AbxToolActionHandler. This
 * replaced the bottom-nav Activity slot — Activity is still reachable from
 * the Dashboard's mini activity panel and the top ContextToolbar.
 */
@Composable
fun ToolboxScreenContent(
    isSessionActive: Boolean,
    onToolSelected: (ToolDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        contentPadding = PaddingValues(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        item {
            Text(
                text = "Toolbox",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Bridge these into your LLM workflow. Each tool only sees the files and operations it declares.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.sm)
            )

            if (!isSessionActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Spacing.sm + 2.dp))
                        .background(MaterialTheme.abxStatusColors.warningContainer)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "No active session — tools that read or write files will be blocked until you start one.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.abxStatusColors.warning
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }

        ToolCatalog.categories.forEach { category ->
            item {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs)
                )
            }
            items(category.tools, key = { it.id }) { tool ->
                // Shared row component — same rhythm as Access/Activity lists,
                // instead of each screen building its own Row(icon, title,
                // subtitle, trailing) from scratch. Icon container is neutral
                // gray, not blue — a tool being listed isn't "active" or
                // "primary" by default.
                ABXListRow(
                    title = tool.title,
                    subtitle = tool.description,
                    icon = tool.icon,
                    onClick = { onToolSelected(tool) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(Spacing.xl)) }
    }
}
