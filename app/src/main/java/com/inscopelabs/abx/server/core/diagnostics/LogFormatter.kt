package com.inscopelabs.abx.server.core.diagnostics

import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogFormatter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun format(level: String, component: String, message: String, throwable: Throwable? = null): String {
        val timestamp = dateFormat.format(Date())
        val pid = Process.myPid()
        val threadName = Thread.currentThread().name
        val sessionId = SessionManager.sessionId
        val header = "$timestamp [$pid:$threadName] [$level] [SESS:$sessionId] [$component]: $message"
        
        return if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            "$header\n$sw"
        } else {
            header
        }
    }
}
