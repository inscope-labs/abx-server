package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DiagnosticBundle {
    fun createBundle(context: Context): File {
        val bundleFile = File(context.cacheDir, "abx_diagnostic_bundle.zip")
        if (bundleFile.exists()) {
            bundleFile.delete()
        }

        ZipOutputStream(FileOutputStream(bundleFile)).use { zos ->
            // 1. Device Info
            zos.putNextEntry(ZipEntry("device_info.txt"))
            zos.write(DeviceInformation.getDeviceMetadata(context).toByteArray())
            zos.closeEntry()

            // 2. Startup Timeline
            zos.putNextEntry(ZipEntry("startup_timeline.txt"))
            zos.write(StartupDiagnostics.getTimeline().toByteArray())
            zos.closeEntry()

            // 3. Runtime Health
            zos.putNextEntry(ZipEntry("runtime_health.txt"))
            zos.write(RuntimeDiagnostics.captureSnapshot(context).toByteArray())
            zos.closeEntry()

            // 4. Main Diagnostics Log
            val logFile = Logger.getLogFile()
            if (logFile != null && logFile.exists()) {
                zos.putNextEntry(ZipEntry("diagnostics.log"))
                logFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }

            // 5. Additional Rotated Logs
            LogRotationManager.getAllLogFiles(context).forEach { file ->
                if (file.name != "diagnostics.log") {
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }

        return bundleFile
    }
}
