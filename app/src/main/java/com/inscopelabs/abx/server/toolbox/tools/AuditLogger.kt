package com.inscopelabs.abx.server.toolbox.tools

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * Append‑only NDJSON audit log – exactly like the Node.js implementation.
 * Never throws; errors are logged to Logcat.
 */
class AuditLogger(private val context: Context) {

    private val auditFile: File = Config.getAuditFile(context)

    init {
        auditFile.parentFile?.mkdirs()
    }

    @Synchronized
    fun audit(event: String, fields: Map<String, Any?> = emptyMap()) {
        try {
            val obj = JSONObject().apply {
                put("ts", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .format(java.util.Date()))
                put("pid", android.os.Process.myPid())
                put("uid", android.os.Process.myUid())
                put("event", event)
                fields.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Number -> put(key, value)
                        is Boolean -> put(key, value)
                        null -> put(key, JSONObject.NULL)
                        else -> put(key, value.toString())
                    }
                }
            }
            PrintWriter(FileWriter(auditFile, true)).use { writer ->
                writer.println(obj.toString())
            }
        } catch (e: Exception) {
            Log.w("AuditLogger", "Audit write failed: ${e.message}")
        }
    }

    fun getAuditLog(): String {
        return auditFile.takeIf { it.exists() }?.readText() ?: ""
    }

    fun clearAuditLog() {
        auditFile.delete()
    }
}
