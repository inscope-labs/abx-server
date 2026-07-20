package com.inscopelabs.abx.server.toolbox.tools

import android.net.Uri

// Allowed purposes (same as Node.js)
enum class Purpose(val value: String) {
    DEBUGGING("debugging"),
    IMPLEMENTATION("implementation"),
    UNDERSTANDING("understanding"),
    REFERENCE("reference"),
    EXPLORATION("exploration")
}

data class SelectedItem(
    val uri: Uri,                   // SAF URI
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    var purpose: Purpose = Purpose.IMPLEMENTATION,  // per‑file override
    var priority: Int = 5           // 1–10, per‑file override
)

data class ContextSelection(
    val id: String,                 // UUID
    val name: String,               // user‑given name
    val timestamp: Long,
    val items: List<SelectedItem>,
    val defaultPurpose: Purpose,
    val defaultPriority: Int
)

data class BuildOptions(
    val selectionId: String,
    val headerText: String = "",
    val separatorText: String = "\n---\n",
    val globalMaxTokens: Int = Config.DEFAULT_MAX_TOKENS,
    val maxFileSizeKb: Int = Config.DEFAULT_FILE_KB,
    val maxDirFiles: Int = Config.DEFAULT_DIR_FILES,
    val forceAll: Boolean = false
)

data class BuildManifest(
    val ok: Boolean,
    val name: String,
    val outputDir: String,          // SAF Uri string
    val durationMs: Long,
    val partCount: Int,
    val fileCount: Int,
    val totalTokens: Int,
    val totalBytes: Long,
    val parts: List<OutputPart>,
    val files: List<ProcessedFile>,
    val skippedFiles: List<SkippedFile>? = null,
    val skippedDirs: List<SkippedDir>? = null
)

data class OutputPart(
    val file: String,               // file name
    val bytes: Long,
    val part: Int? = null           // part number if split
)

data class ProcessedFile(
    val path: String,               // original SAF path/name
    val purpose: String,
    val mode: String = "full",      // future use
    val tokens: Int,
    val bytes: Long
)

data class SkippedFile(val path: String, val bytes: Long, val limit: Int)
data class SkippedDir(val dir: String, val fileCount: Int, val limit: Int)

// UI Preview
data class PreviewChunk(
    val index: Int,
    val text: String,
    val tokenCount: Int,
    val byteSize: Int,
    val isComplete: Boolean = false
)

// Progress (for streaming)
sealed class BuildProgress {
    data class Starting(val name: String) : BuildProgress()
    data class FileProgress(val current: Int, val total: Int, val path: String) : BuildProgress()
    data class Complete(val manifest: BuildManifest) : BuildProgress()
    data class Error(val message: String) : BuildProgress()
}
