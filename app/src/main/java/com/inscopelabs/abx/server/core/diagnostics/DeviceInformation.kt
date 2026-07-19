package com.inscopelabs.abx.server.core.diagnostics

import android.content.Context
import android.os.Build
import com.inscopelabs.abx.server.BuildConfig

object DeviceInformation {
    fun getDeviceMetadata(context: Context): String {
        return buildString {
            appendLine("=== DEVICE INFORMATION ===")
            appendLine("Manufacturer   : ${Build.MANUFACTURER}")
            appendLine("Model          : ${Build.MODEL}")
            appendLine("Brand          : ${Build.BRAND}")
            appendLine("Device         : ${Build.DEVICE}")
            appendLine("Product        : ${Build.PRODUCT}")
            appendLine("Android OS     : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("CPU ABI        : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("App Version    : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package Name   : ${context.packageName}")
            appendLine("Language       : ${java.util.Locale.getDefault()}")
        }
    }
}
