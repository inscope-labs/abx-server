package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context
import java.io.File

object LogRotationManager {
    private const val MAX_FILE_SIZE = 1 * 1024 * 1024 // 1 MB
    private const val MAX_ARCHIVE_FILES = 5

    @Synchronized
    fun checkAndRotate(logFile: File, context: Context) {
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
            rotate(logFile, context)
        }
    }

    private fun rotate(logFile: File, context: Context) {
        for (i in MAX_ARCHIVE_FILES - 1 downTo 1) {
            val current = File(context.filesDir, "${logFile.name}.$i")
            if (current.exists()) {
                val next = File(context.filesDir, "${logFile.name}.${i + 1}")
                if (next.exists()) {
                    next.delete()
                }
                current.renameTo(next)
            }
        }
        val firstBackup = File(context.filesDir, "${logFile.name}.1")
        if (firstBackup.exists()) {
            firstBackup.delete()
        }
        logFile.renameTo(firstBackup)
    }

    fun getAllLogFiles(context: Context): List<File> {
        val list = mutableListOf<File>()
        val mainFile = File(context.filesDir, "diagnostics.log")
        if (mainFile.exists()) list.add(mainFile)
        for (i in 1..MAX_ARCHIVE_FILES) {
            val f = File(context.filesDir, "diagnostics.log.$i")
            if (f.exists()) list.add(f)
        }
        return list
    }
}
