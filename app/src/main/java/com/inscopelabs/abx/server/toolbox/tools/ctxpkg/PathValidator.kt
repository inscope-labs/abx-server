package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.net.Uri
import android.util.Log

/**
 * Security checks – mirrors Node.js assertContained().
 * In SAF, most traversal attacks are prevented, but we keep the guards
 * for display names, relative paths inside a tree, and to avoid infinite loops.
 */
object PathValidator {
    private const val TAG = "PathValidator"

    fun validateName(label: String, name: String): String {
        require(name.isNotBlank()) { "$label: must be non‑empty" }

        // 1. Null byte
        if (name.contains('\u0000')) {
            throw SecurityException("$label: null byte not allowed")
        }

        // 2. URL‑encoded traversal (just in case)
        if (Regex("%(?:2e|2f|5c)", RegexOption.IGNORE_CASE).containsMatchIn(name)) {
            throw SecurityException("$label: URL‑encoded traversal not allowed")
        }

        // 3. Dot‑dot after naive decode
        val decoded = try {
            name.replace(Regex("%[0-9a-f]{2}", RegexOption.IGNORE_CASE)) {
                try {
                    it.value.substring(1).toInt(16).toChar().toString()
                } catch (e: NumberFormatException) {
                    it.value
                }
            }
        } catch (e: Exception) {
            name
        }
        if (Regex("(?:^|[/\\\\])\\.\\.(?:[/\\\\]|$)").containsMatchIn(decoded)) {
            throw SecurityException("$label: path traversal (..) not allowed")
        }

        return name
    }

    fun validateRelativePath(treeRoot: Uri, relativePath: String): String {
        val clean = validateName("relativePath", relativePath)
        // Additional SAF‑specific: ensure it doesn't try to break out of tree
        // by checking for leading / or absolute patterns.
        val normalized = clean.trim().replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains("://")) {
            throw SecurityException("relativePath: must be a simple relative path")
        }
        return normalized
    }
}
