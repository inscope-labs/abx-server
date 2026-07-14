package com.inscopelabs.abx.server

import android.app.Application
import com.inscopelabs.abx.server.boot.BootGuard

class HelloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            BootGuard.stageStart("AppInit")
            BootGuard.stageSuccess("AppInit")
        } catch (t: Throwable) {
            BootGuard.recordFailure(this, "Application.onCreate", t)
        }
    }
}
