package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.inscopelabs.abx.server.toolbox.tools.ctxpkg.SafUtils.readTextFromUri

/**
 * Handles reading all files, applying header/separator, and generating
 * preview chunks for the UI – with per‑file purpose/priority overrides.
 */
class TextAggregator(private val context: Context, private val auditLogger: AuditLogger) {

    /**
     * Returns a flow of PreviewChunk. Each chunk is roughly page‑sized
     * for smooth UI updates.
     */
    suspend fun generatePreview(
        selection: ContextSelection,
        options: BuildOptions
    ): Flow<PreviewChunk> = flow {
        val allUris = SafUtils.flattenSelection(
            context = context,
            selection = selection,
            maxDirFiles = options.maxDirFiles,
            forceAll = options.forceAll,
            auditLogger = auditLogger
        )
        val totalFiles = allUris.size
        var fileIndex = 0
        var chunkIndex = 0
        val maxChunkSize = 4096 // characters

        // Emit header
        if (options.headerText.isNotEmpty()) {
            emit(
                PreviewChunk(
                    index = chunkIndex++,
                    text = options.headerText,
                    tokenCount = estimateTokens(options.headerText),
                    byteSize = options.headerText.toByteArray().size,
                    isComplete = false
                )
            )
        }

        for ((uri, displayName, fileSize) in allUris) {
            // Find the purpose/priority override from selection if present, else defaults
            val item = selection.items.find { it.uri == uri }
            val purpose = item?.purpose?.value ?: selection.defaultPurpose.value
            val priority = item?.priority ?: selection.defaultPriority

            auditLogger.audit("preview-file", mapOf(
                "file" to displayName,
                "purpose" to purpose,
                "priority" to priority
            ))

            val content = try {
                context.readTextFromUri(uri)
            } catch (e: Exception) {
                val errMsg = "[[ERROR reading $displayName: ${e.message}]]"
                emit(PreviewChunk(
                    index = chunkIndex++,
                    text = errMsg,
                    tokenCount = estimateTokens(errMsg),
                    byteSize = errMsg.toByteArray().size,
                    isComplete = false
                ))
                continue
            }

            val separator = if (fileIndex > 0) options.separatorText else ""
            val fullText = separator + content

            // Split into chunks for UI rendering
            var offset = 0
            while (offset < fullText.length) {
                val end = minOf(offset + maxChunkSize, fullText.length)
                val chunk = fullText.substring(offset, end)
                emit(
                    PreviewChunk(
                        index = chunkIndex++,
                        text = chunk,
                        tokenCount = estimateTokens(chunk),
                        byteSize = chunk.toByteArray().size,
                        isComplete = false
                    )
                )
                offset = end
                // simulate streaming delay
                delay(2)
            }

            fileIndex++
        }

        emit(PreviewChunk(
            index = chunkIndex,
            text = "",
            tokenCount = 0,
            byteSize = 0,
            isComplete = true
        ))
    }

    // Token estimator – same as Node.js placeholder
    private fun estimateTokens(text: String): Int = text.length / 4
}
