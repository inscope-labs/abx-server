package com.inscopelabs.abx.server.core.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StartupDiagnostics {
    private val events = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun recordEvent(eventName: String) {
        val timestamp = dateFormat.format(Date())
        val line = "[$timestamp] $eventName"
        events.add(line)
        Logger.i("StartupDiagnostics", eventName)
    }

    @Synchronized
    fun getTimeline(): String {
        return events.joinToString("\n")
    }
}
