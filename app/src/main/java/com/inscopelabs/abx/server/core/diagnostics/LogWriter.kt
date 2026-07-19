package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class LogWriter(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val logFile = File(context.filesDir, "diagnostics.log")

    fun write(line: String) {
        executor.execute {
            try {
                LogRotationManager.checkAndRotate(logFile, context)
                FileOutputStream(logFile, true).bufferedWriter().use { writer ->
                    writer.write(line)
                    writer.newLine()
                }
            } catch (e: Exception) {
                Log.e("LogWriter", "Error writing log line", e)
            }
        }
    }

    fun getLogFile(): File = logFile
}
