package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context
import android.util.Log
import java.io.File

object Logger {
    private var logWriter: LogWriter? = null

    fun initialize(context: Context) {
        logWriter = LogWriter(context.applicationContext)
    }

    fun d(component: String, message: String) {
        Log.d(component, message)
        write("DEBUG", component, message)
    }

    fun i(component: String, message: String) {
        Log.i(component, message)
        write("INFO", component, message)
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        Log.w(component, message, throwable)
        write("WARN", component, message, throwable)
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        Log.e(component, message, throwable)
        write("ERROR", component, message, throwable)
    }

    private fun write(level: String, component: String, message: String, throwable: Throwable? = null) {
        val formatted = LogFormatter.format(level, component, message, throwable)
        logWriter?.write(formatted)
    }

    fun getLogFile(): File? = logWriter?.getLogFile()
}
