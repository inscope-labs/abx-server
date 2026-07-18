package com.inscopelabs.abx.server.toolbox

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A single JS tool bundled under assets/tools/<assetDir>/index.html.
 * allowedOperations/allowedRoots scope exactly what MCP operations that
 * tool's Capability token is minted with — a tool only gets the access it
 * declares here, not full session-wide access.
 */
data class ToolDefinition(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val assetDir: String,
    val allowedOperations: List<String>,
    val allowedRoots: List<String>
)

data class ToolCategory(
    val id: String,
    val title: String,
    val tools: List<ToolDefinition>
)

object ToolCatalog {
    val categories: List<ToolCategory> = listOf(
        ToolCategory(
            id = "context",
            title = "Context Tools",
            tools = listOf(
                ToolDefinition(
                    id = "context-inspector",
                    title = "Context Inspector",
                    description = "Browse readable paths and preview file contents before handing them to an LLM.",
                    icon = Icons.Default.FindInPage,
                    assetDir = "context-inspector",
                    allowedOperations = listOf("file_exists", "get_file_metadata", "read_file", "list_directory"),
                    allowedRoots = listOf("/storage/emulated/0/Documents")
                ),
                ToolDefinition(
                    id = "diff-preview",
                    title = "Diff Preview",
                    description = "Compare a proposed LLM edit against the current file before it's written.",
                    icon = Icons.Default.Compare,
                    assetDir = "diff-preview",
                    allowedOperations = listOf("read_file", "get_file_version"),
                    allowedRoots = listOf("/storage/emulated/0/Documents")
                )
            )
        ),
        ToolCategory(
            id = "prompt",
            title = "Prompt Utilities",
            tools = listOf(
                ToolDefinition(
                    id = "prompt-composer",
                    title = "Prompt Composer",
                    description = "Assemble a prompt from bridged file snippets with token-count estimates.",
                    icon = Icons.Default.Edit,
                    assetDir = "prompt-composer",
                    allowedOperations = listOf("read_file", "list_directory"),
                    allowedRoots = listOf("/storage/emulated/0/Documents")
                )
            )
        ),
        ToolCategory(
            id = "diagnostics",
            title = "Session Diagnostics",
            tools = listOf(
                ToolDefinition(
                    id = "session-monitor",
                    title = "Session Monitor",
                    description = "Live view of session TTL, capability scope, and the last operations executed.",
                    icon = Icons.Default.MonitorHeart,
                    assetDir = "session-monitor",
                    allowedOperations = emptyList(),
                    allowedRoots = emptyList()
                )
            )
        )
    )

    fun findById(id: String): ToolDefinition? =
        categories.flatMap { it.tools }.firstOrNull { it.id == id }
}
