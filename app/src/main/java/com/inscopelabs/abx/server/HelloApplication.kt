package com.inscopelabs.abx.server

import android.app.Application
import android.content.Context
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager
import com.inscopelabs.abx.server.core.audit.AuditLog
import com.inscopelabs.abx.server.boot.BootGuard
import com.inscopelabs.abx.server.core.diagnostics.DiagnosticsInitializer
import com.inscopelabs.abx.server.core.diagnostics.StartupDiagnostics

class HelloApplication : Application() {
    var keyStoreManager: KeyStoreManager? = null
        internal set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupDiagnostics.recordEvent("Application attachBaseContext finished")
    }

    override fun onCreate() {
        // Initialize diagnostics as the absolute first action in onCreate
        DiagnosticsInitializer.initialize(this)
        StartupDiagnostics.recordEvent("HelloApplication.onCreate started")
        
        super.onCreate()
        
        // Install global crash handler FIRST.
        Thread.setDefaultUncaughtExceptionHandler(
            com.inscopelabs.abx.server.core.diagnostics.GlobalExceptionHandler(this)
        )

        try {
            BootGuard.stageStart("KeyStoreManager")
            val km = KeyStoreManager(this)
            keyStoreManager = km
            BootGuard.stageSuccess("KeyStoreManager")

            BootGuard.stageStart("AuditLog")
            AuditLog.initialize(this, km)
            BootGuard.stageSuccess("AuditLog")
        } catch (t: Throwable) {
            BootGuard.recordFailure(this, "Application.onCreate", t)
        }
    }
}
