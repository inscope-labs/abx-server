package com.inscopelabs.abx.server.core.diagnostics

import android.app.ActivityManager
import android.content.Context

object RuntimeDiagnostics {
    fun captureSnapshot(context: Context): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory

        val threadCount = Thread.activeCount()

        return buildString {
            appendLine("=== RUNTIME HEALTH SNAPSHOT ===")
            appendLine("Heap Max Memory   : $maxMemory MB")
            appendLine("Heap Total Memory : $totalMemory MB")
            appendLine("Heap Used Memory  : $usedMemory MB")
            appendLine("Heap Free Memory  : $freeMemory MB")
            appendLine("Active Thread Count: $threadCount")
            
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                appendLine("System Low Memory : ${memoryInfo.lowMemory}")
                appendLine("System Avail Memory: ${memoryInfo.availMem / (1024 * 1024)} MB")
                appendLine("System Total Memory: ${memoryInfo.totalMem / (1024 * 1024)} MB")
            }
        }
    }
}
