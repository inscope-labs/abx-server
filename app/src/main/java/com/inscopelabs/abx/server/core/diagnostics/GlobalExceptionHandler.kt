package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context
import android.content.Intent
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
            val referenceCode = generateReferenceCode()
            val crashReport = buildCrashReport(thread, throwable, referenceCode)

            writeCrashLog(crashReport)

            sendToCrashlytics(thread, throwable)

            launchErrorActivity(throwable, crashReport, referenceCode)
        } catch (e: Exception) {
            // Never allow diagnostics / crash handling to crash the app or recurse.
            try {
                Log.e("ABX_CRASH", "Exception inside exception handler", e)
            } catch (ignored: Throwable) {}
        }

        // Deliberately NOT chaining to the platform default handler here.
        // launchErrorActivity() already starts a FLAG_ACTIVITY_NEW_TASK intent,
        // which Android will honor even after this process dies — the app
        // effectively respawns straight into the error screen instead of the
        // bare OS "App has stopped" dialog. If launchErrorActivity() itself
        // failed above, fall through to the default handler so the user
        // still sees *something* rather than a silent hang.
        val startedCrashActivity = crashActivityLaunched
        if (!startedCrashActivity) {
            defaultHandler?.uncaughtException(thread, throwable)
        }

        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess()
    }

    @Volatile
    private var crashActivityLaunched = false

    /**
     * Short opaque code correlating the on-screen error with its full entry
     * in crash_logs.txt, without exposing exception internals on the
     * release-build screen. Not a security token — just a human-quotable
     * lookup key for support.
     */
    private fun generateReferenceCode(): String =
        "ABX-" + java.lang.Long.toString(System.currentTimeMillis(), 36).uppercase(Locale.US)

    /**
     * Starts the appropriate error screen in a new task so it survives this
     * process being killed immediately afterward. Debug builds get
     * CrashActivity's full developer detail (exception type, stack trace);
     * release builds get UserFacingErrorActivity's calm, detail-free screen
     * with just the reference code — the full report stays in
     * crash_logs.txt and is only attached to a share Intent if the user
     * explicitly asks. Mirrors the classic TopExceptionHandler/
     * DisplayExceptionDataActivity pattern either way.
     */
    private fun launchErrorActivity(throwable: Throwable, fullReport: String, referenceCode: String) {
        val intent = if (BuildConfig.DEBUG) {
            val metadata = buildString {
                appendLine("Manufacturer: ${Build.MANUFACTURER}")
                appendLine("Model: ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
            val stackWriter = StringWriter()
            throwable.printStackTrace(PrintWriter(stackWriter))

            Intent(appContext, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("extra_exception_type", throwable.javaClass.name)
                putExtra("extra_message", throwable.message ?: "No message")
                putExtra("extra_metadata", metadata)
                putExtra("extra_stack_trace", stackWriter.toString())
                putExtra("extra_full_report", fullReport)
            }
        } else {
            Intent(appContext, UserFacingErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("extra_reference_code", referenceCode)
                putExtra("extra_full_report", fullReport)
            }
        }
        appContext.startActivity(intent)
        crashActivityLaunched = true
    }

    /**
     * Build a complete crash report with system and app metadata.
     */
    private fun buildCrashReport(
        thread: Thread,
        throwable: Throwable,
        referenceCode: String
    ): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))

        return buildString {
            appendLine("====================================================")
            appendLine("ABX GLOBAL CRASH REPORT")
            appendLine("====================================================")

            appendLine("Reference Code : $referenceCode")
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
