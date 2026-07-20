package com.inscopelabs.abx.server.toolbox.tools.ctxpkg

import android.content.Context
import android.content.SharedPreferences

/**
 * Central configuration – maps directly to the Node.js CONFIG object.
 * Overrides can be stored in SharedPreferences (equivalent to environment variables).
 */
object Config {
    // Token budget
    const val MIN_MAX_TOKENS = 10_000
    const val MAX_MAX_TOKENS = 800_000
    const val DEFAULT_MAX_TOKENS = 200_000

    // Output file split (KB)
    const val MIN_FILE_KB = 0        // 0 = no split
    const val MAX_FILE_KB = 4_096    // 4 MB
    const val DEFAULT_FILE_KB = 0    // no split

    // Directory file‑count guard
    const val MIN_DIR_FILES = 10
    const val MAX_DIR_FILES = 50_000
    const val DEFAULT_DIR_FILES = 200

    // Audit file name
    private const val AUDIT_FILE_NAME = "context-pkg-audit.ndjson"
    private const val PREFS_NAME = "context_pkg_config"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun getPrefs(): SharedPreferences {
        val p = prefs
        if (p != null) return p
        synchronized(this) {
            val p2 = prefs
            if (p2 != null) return p2
            throw IllegalStateException("Config must be initialized before use")
        }
    }

    fun getInt(key: String, default: Int): Int = getPrefs().getInt(key, default)
    fun getLong(key: String, default: Long): Long = getPrefs().getLong(key, default)
    fun getBoolean(key: String, default: Boolean): Boolean = getPrefs().getBoolean(key, default)

    fun updateConfig(context: Context, block: (SharedPreferences.Editor) -> Unit) {
        initialize(context)
        val editor = getPrefs().edit()
        block(editor)
        editor.apply()
    }

    fun getAuditFile(context: Context): java.io.File =
        java.io.File(context.filesDir, AUDIT_FILE_NAME)
}
