package com.inscopelabs.abx.server

import android.app.Application
import android.content.Context
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager

class HelloApplication : Application() {
    var keyStoreManager: KeyStoreManager? = null
        internal set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(
            com.inscopelabs.abx.server.core.diagnostics.GlobalExceptionHandler(this)
        )
    }
}
