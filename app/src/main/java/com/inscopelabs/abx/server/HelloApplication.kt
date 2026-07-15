package com.inscopelabs.abx.server

import android.app.Application
import com.inscopelabs.abx.server.core.keystore.KeyStoreManager
import com.inscopelabs.abx.server.boot.BootGuard

class HelloApplication : Application() {
    var keyStoreManager: KeyStoreManager? = null
        internal set

    override fun onCreate() {
        super.onCreate()
        try {
            BootGuard.stageStart("KeyStoreManager")
            val km = KeyStoreManager(this)
            keyStoreManager = km
            BootGuard.stageSuccess("KeyStoreManager")
        } catch (t: Throwable) {
            BootGuard.recordFailure(this, "Application.onCreate", t)
        }
    }
}
