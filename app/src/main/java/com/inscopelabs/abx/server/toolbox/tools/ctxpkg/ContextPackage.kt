package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The single entry point for any Android Activity.
 * Wraps all underlying components and exposes UI‑friendly APIs.
 */
class ContextPackage private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ContextPackage? = null

        fun getInstance(context: Context): ContextPackage {
            return instance ?: synchronized(this) {
                instance ?: ContextPackage(context.applicationContext).also {
                    Config.initialize(it.context)
                    instance = it
                }
            }
        }
    }

    private val auditLogger = AuditLogger(context)
    private val contextStore = ContextStore(context)
    private val textAggregator = TextAggregator(context, auditLogger)
    private val packageBuilder = PackageBuilder(context, auditLogger)

    // ─── Selection Management ────────────────────────────────────────────

    fun saveSelection(
        name: String,
        items: List<SelectedItem>,
        defaultPurpose: Purpose = Purpose.IMPLEMENTATION,
        defaultPriority: Int = 5
    ): ContextSelection {
        val selection = ContextStore.createNew(name, items, defaultPurpose, defaultPriority)
        contextStore.saveSelection(selection)
        auditLogger.audit("selection-saved", mapOf(
            "selectionId" to selection.id,
            "name" to name,
            "itemCount" to items.size
        ))
        return selection
    }

    fun loadSelection(id: String): ContextSelection? = contextStore.loadSelection(id)

    fun loadAllSelections(): List<ContextSelection> = contextStore.loadAllSelections()

    fun deleteSelection(id: String): Boolean {
        auditLogger.audit("selection-deleted", mapOf("selectionId" to id))
        return contextStore.deleteSelection(id)
    }

    // ─── Preview (UI streaming) ──────────────────────────────────────────

    suspend fun generatePreview(
        selectionId: String,
        header: String = "",
        separator: String = "\n---\n",
        maxTokens: Int = Config.DEFAULT_MAX_TOKENS
    ): Flow<PreviewChunk> {
        val selection = contextStore.loadSelection(selectionId)
            ?: throw IllegalArgumentException("Selection not found: $selectionId")
        val options = BuildOptions(
            selectionId = selectionId,
            headerText = header,
            separatorText = separator,
            globalMaxTokens = maxTokens
        )
        return textAggregator.generatePreview(selection, options)
    }

    // ─── Build / Export (Download) ──────────────────────────────────────

    suspend fun exportPackage(
        selectionId: String,
        outputTreeUri: Uri,        // user‑picked via ACTION_OPEN_DOCUMENT_TREE
        header: String = "",
        separator: String = "\n---\n",
        maxTokens: Int = Config.DEFAULT_MAX_TOKENS,
        maxFileKb: Int = Config.DEFAULT_FILE_KB,
        maxDirFiles: Int = Config.DEFAULT_DIR_FILES,
        forceAll: Boolean = false
    ): BuildManifest {
        val selection = contextStore.loadSelection(selectionId)
            ?: throw IllegalArgumentException("Selection not found: $selectionId")

        val options = BuildOptions(
            selectionId = selectionId,
            headerText = header,
            separatorText = separator,
            globalMaxTokens = maxTokens,
            maxFileSizeKb = maxFileKb,
            maxDirFiles = maxDirFiles,
            forceAll = forceAll
        )

        var result: BuildManifest? = null
        packageBuilder.buildPackage(selection, options, outputTreeUri)
            .collect { manifest ->
                result = manifest
                auditLogger.audit("export-complete", mapOf(
                    "selectionId" to selectionId,
                    "partCount" to manifest.partCount,
                    "totalBytes" to manifest.totalBytes
                ))
            }

        return result ?: throw IllegalStateException("Build produced no result")
    }

    // ─── Utilities ───────────────────────────────────────────────────────

    fun getAuditLog(): String = auditLogger.getAuditLog()

    fun clearAuditLog() = auditLogger.clearAuditLog()

    fun updateConfig(block: (SharedPreferences.Editor) -> Unit) {
        Config.updateConfig(context, block)
    }
}
