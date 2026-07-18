package com.inscopelabs.abx.server

import android.app.Application
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager
import com.inscopelabs.abx.server.core.audit.AuditLog
import com.inscopelabs.abx.server.boot.BootGuard

class HelloApplication : Application() {
    var keyStoreManager: KeyStoreManager? = null
        internal set

    override fun onCreate() {
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
