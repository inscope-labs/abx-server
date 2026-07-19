package com.inscopelabs.abx.server.core.diagnostics

import android.app.Service
import android.content.Intent
import android.os.IBinder

class DiagnosticService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i("DiagnosticService", "Diagnostic Background Service Started")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i("DiagnosticService", "Diagnostic Background Service Destroyed")
    }
}
