package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context

object DiagnosticsInitializer {
    private var watchdog: AnrWatchdog? = null

    fun initialize(context: Context) {
        StartupDiagnostics.recordEvent("DiagnosticsInitializer.initialize started")
        
        // 1. Logger
        Logger.initialize(context)
        StartupDiagnostics.recordEvent("Logger initialized")

        // 2. Crash reporting manager
        CrashReporterManager.initialize(context)
        StartupDiagnostics.recordEvent("CrashReporterManager initialized")

        // 3. ANR Watchdog
        watchdog = AnrWatchdog().apply { start() }
        StartupDiagnostics.recordEvent("ANR Watchdog started")

        StartupDiagnostics.recordEvent("DiagnosticsInitializer.initialize finished")
    }

    fun shutdown() {
        watchdog?.stopMonitoring()
    }
}
