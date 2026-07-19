package com.inscopelabs.abx.server.core.diagnostics

import java.io.File

class LogViewerAdapter {
    data class LogEntry(
        val timestamp: String,
        val threadInfo: String,
        val level: String,
        val session: String,
        val component: String,
        val message: String
    )

    fun parseLogs(logFile: File): List<LogEntry> {
        if (!logFile.exists()) return emptyList()
        val entries = mutableListOf<LogEntry>()
        try {
            logFile.useLines { lines ->
                lines.forEach { line ->
                    val entry = parseLine(line)
                    if (entry != null) {
                        entries.add(entry)
                    } else if (line.isNotBlank()) {
                        if (entries.isNotEmpty()) {
                            val last = entries.last()
                            entries[entries.lastIndex] = last.copy(message = last.message + "\n" + line)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            entries.add(LogEntry("", "", "ERROR", "", "LogViewerAdapter", "Failed to parse: ${e.message}"))
        }
        return entries
    }

    private fun parseLine(line: String): LogEntry? {
        try {
            if (!line.startsWith("2")) return null
            val parts = line.split(" ", limit = 3)
            if (parts.size < 3) return null
            val timestamp = parts[0] + " " + parts[1]
            val remaining = parts[2]
            
            val threadStart = remaining.indexOf('[')
            val threadEnd = remaining.indexOf(']')
            if (threadStart == -1 || threadEnd == -1) return null
            val threadInfo = remaining.substring(threadStart + 1, threadEnd)
            
            val levelStart = remaining.indexOf('[', threadEnd + 1)
            val levelEnd = remaining.indexOf(']', threadEnd + 1)
            if (levelStart == -1 || levelEnd == -1) return null
            val level = remaining.substring(levelStart + 1, levelEnd)
            
            val sessStart = remaining.indexOf('[', levelEnd + 1)
            val sessEnd = remaining.indexOf(']', levelEnd + 1)
            if (sessStart == -1 || sessEnd == -1) return null
            val session = remaining.substring(sessStart + 1, sessEnd)
            
            val compStart = remaining.indexOf('[', sessEnd + 1)
            val compEnd = remaining.indexOf(']', sessEnd + 1)
            if (compStart == -1 || compEnd == -1) return null
            val component = remaining.substring(compStart + 1, compEnd)
            
            val msgIndex = remaining.indexOf(':', compEnd + 1)
            if (msgIndex == -1) return null
            val message = remaining.substring(msgIndex + 1).trim()
            
            return LogEntry(timestamp, threadInfo, level, session, component, message)
        } catch (e: Exception) {
            return null
        }
    }
}
