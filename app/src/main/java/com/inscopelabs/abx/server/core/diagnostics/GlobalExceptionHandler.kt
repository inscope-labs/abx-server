package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.inscopelabs.abx.server.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global uncaught exception handler for ABX Server.
 *
 * Responsibilities:
 * - Log every uncaught Java/Kotlin exception to logcat.
 * - Persist crash reports to internal storage safely with log rotation.
 * - Dynamically forward crashes to Firebase Crashlytics if available on classpath.
 * - Chained to Android's default exception handler to ensure standard system crash behavior.
 */
class GlobalExceptionHandler(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val CRASH_LOG_FILE = "crash_logs.txt"
        private const val MAX_LOG_SIZE = 5L * 1024L * 1024L // 5 MB

        private val fileLock = Any()
    }

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    private val appContext: Context = context.applicationContext

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable
    ) {
        try {
            val crashReport = buildCrashReport(thread, throwable)

            writeCrashLog(crashReport)

            sendToCrashlytics(thread, throwable)
        } catch (e: Exception) {
            // Never allow diagnostics / crash handling to crash the app or recurse.
            try {
                Log.e("ABX_CRASH", "Exception inside exception handler", e)
            } catch (ignored: Throwable) {}
        } finally {
            // Guarantee that the default Android uncaught exception handler is always invoked
            defaultHandler?.uncaughtException(thread, throwable)
                ?: run {
                    // Fallback termination to prevent deadlock or hanging state
                    android.os.Process.killProcess(android.os.Process.myPid())
                    exitProcess()
                }
        }
    }

    /**
     * Build a complete crash report with system and app metadata.
     */
    private fun buildCrashReport(
        thread: Thread,
        throwable: Throwable
    ): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))

        return buildString {
            appendLine("====================================================")
            appendLine("ABX GLOBAL CRASH REPORT")
            appendLine("====================================================")

            appendLine("Timestamp      : ${
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.US
                ).format(Date())
            }")

            appendLine("Thread         : ${thread.name}")
            appendLine("Thread ID      : ${thread.id}")

            appendLine()
            appendLine("APPLICATION")

            appendLine("Package        : ${appContext.packageName}")
            appendLine("Version Name   : ${BuildConfig.VERSION_NAME}")
            appendLine("Version Code   : ${BuildConfig.VERSION_CODE}")
            appendLine("Debug Build    : ${BuildConfig.DEBUG}")

            appendLine()
            appendLine("DEVICE")

            appendLine("Manufacturer   : ${Build.MANUFACTURER}")
            appendLine("Brand          : ${Build.BRAND}")
            appendLine("Model          : ${Build.MODEL}")
            appendLine("Device         : ${Build.DEVICE}")
            appendLine("Product        : ${Build.PRODUCT}")

            appendLine()
            appendLine("ANDROID")

            appendLine("Release        : ${Build.VERSION.RELEASE}")
            appendLine("SDK            : ${Build.VERSION.SDK_INT}")
            appendLine("Codename       : ${Build.VERSION.CODENAME}")

            appendLine()
            appendLine("EXCEPTION")

            appendLine("Type           : ${throwable.javaClass.name}")
            appendLine("Message        : ${throwable.message}")

            appendLine()
            appendLine("STACK TRACE")
            appendLine(writer.toString())

            var cause = throwable.cause
            while (cause != null) {
                appendLine()
                appendLine("CAUSED BY")
                appendLine(Log.getStackTraceString(cause))
                cause = cause.cause
            }

            appendLine("====================================================")
            appendLine()
        }
    }

    /**
     * Safely writes crash report to internal storage with log rotation.
     */
    private fun writeCrashLog(report: String) {
        synchronized(fileLock) {
            try {
                val file = File(appContext.filesDir, CRASH_LOG_FILE)
                
                // Rotation check: if current file plus new report exceeds the limit, rotate
                if (file.exists() && (file.length() + report.length > MAX_LOG_SIZE)) {
                    val rotatedFile = File(appContext.filesDir, "$CRASH_LOG_FILE.1")
                    if (rotatedFile.exists()) {
                        rotatedFile.delete()
                    }
                    file.renameTo(rotatedFile)
                }

                FileOutputStream(file, true).bufferedWriter().use { writer ->
                    writer.write(report)
                    writer.flush()
                }
            } catch (e: Exception) {
                try {
                    Log.e("ABX_CRASH", "Failed to write crash log to file", e)
                } catch (ignored: Throwable) {}
            }
        }
    }

    /**
     * Send crash details dynamically to Firebase Crashlytics if present on the classpath.
     */
    private fun sendToCrashlytics(
        thread: Thread,
        throwable: Throwable
    ) {
        try {
            // Use reflection to detect if Firebase Crashlytics is on the classpath.
            // This prevents hard dependency / compilation errors while maintaining forward compatibility.
            val crashlyticsClass = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val getInstanceMethod = crashlyticsClass.getMethod("getInstance")
            val crashlyticsInstance = getInstanceMethod.invoke(null)

            val logMethod = crashlyticsClass.getMethod("log", String::class.java)
            logMethod.invoke(crashlyticsInstance, "Global Exception Handler")

            val setCustomKeyStringMethod = crashlyticsClass.getMethod(
                "setCustomKey",
                String::class.java,
                String::class.java
            )
            setCustomKeyStringMethod.invoke(crashlyticsInstance, "thread_name", thread.name)
            setCustomKeyStringMethod.invoke(crashlyticsInstance, "thread_id", thread.id.toString())
            setCustomKeyStringMethod.invoke(crashlyticsInstance, "manufacturer", Build.MANUFACTURER)
            setCustomKeyStringMethod.invoke(crashlyticsInstance, "model", Build.MODEL)
            setCustomKeyStringMethod.invoke(crashlyticsInstance, "version_name", BuildConfig.VERSION_NAME)

            val setCustomKeyLongMethod = crashlyticsClass.getMethod(
                "setCustomKey",
                String::class.java,
                Long::class.java
            )
            setCustomKeyLongMethod.invoke(crashlyticsInstance, "timestamp", System.currentTimeMillis())

            val setCustomKeyIntMethod = crashlyticsClass.getMethod(
                "setCustomKey",
                String::class.java,
                Int::class.java
            )
            setCustomKeyIntMethod.invoke(crashlyticsInstance, "android_sdk", Build.VERSION.SDK_INT)
            setCustomKeyIntMethod.invoke(crashlyticsInstance, "version_code", BuildConfig.VERSION_CODE)

            val recordExceptionMethod = crashlyticsClass.getMethod(
                "recordException",
                Throwable::class.java
            )
            recordExceptionMethod.invoke(crashlyticsInstance, throwable)
        } catch (_: Exception) {
            // Ignore if Firebase Crashlytics is not integrated or fails.
        }
    }

    /**
     * Last resort process termination.
     */
    private fun exitProcess() {
        kotlin.system.exitProcess(10)
    }
}
