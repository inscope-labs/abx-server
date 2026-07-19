package com.inscopelabs.abx.server.core.diagnostics

import java.io.File

object LogSearchEngine {
    fun search(logFile: File, query: String): List<String> {
        if (!logFile.exists()) return emptyList()
        val results = mutableListOf<String>()
        try {
            logFile.useLines { lines ->
                lines.forEach { line ->
                    if (query.isBlank() || line.contains(query, ignoreCase = true)) {
                        results.add(line)
                    }
                }
            }
        } catch (e: Exception) {
            results.add("Error searching logs: ${e.message}")
        }
        return results
    }
}
