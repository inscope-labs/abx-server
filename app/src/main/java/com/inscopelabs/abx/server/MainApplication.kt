package com.inscopelabs.abx.server

import android.app.Application
import android.content.Context
import com.inscopelabs.abx.server.core.diagnostics.CrashReporterManager
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager

class MainApplication : Application() {
    var keyStoreManager: KeyStoreManager? = null
        internal set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()

        // Must run before the exception handler is installed: GlobalExceptionHandler
        // and AnrWatchdog (started later, in MainActivity's startup sequence) both
        // call CrashReporterManager.reportCrash(), which throws
        // UninitializedPropertyAccessException if this hasn't run first.
        CrashReporterManager.initialize(this)

        Thread.setDefaultUncaughtExceptionHandler(
            com.inscopelabs.abx.server.core.diagnostics.GlobalExceptionHandler(this)
        )
    }
}
