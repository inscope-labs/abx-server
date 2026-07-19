package com.inscopelabs.abx.server.core.diagnostics

import android.os.Handler
import android.os.Looper

class AnrWatchdog(private val timeoutMs: Long = 5000L) : Thread("anr-watchdog") {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var tick = 0L
    @Volatile private var isRunning = true

    private val ticker = Runnable {
        tick = (tick + 1) % Long.MAX_VALUE
    }

    override fun run() {
        Logger.i("AnrWatchdog", "ANR Watchdog started with timeout $timeoutMs ms")
        while (isRunning) {
            val lastTick = tick
            handler.post(ticker)
            
            try {
                sleep(timeoutMs)
            } catch (e: InterruptedException) {
                Logger.w("AnrWatchdog", "ANR Watchdog interrupted", e)
                break
            }

            if (tick == lastTick) {
                val mainThread = Looper.getMainLooper().thread
                val stackTrace = mainThread.stackTrace
                val exception = ANRException(stackTrace)
                
                Logger.e("AnrWatchdog", "ANR DETECTED! Main thread blocked for more than $timeoutMs ms", exception)
                CrashReporterManager.reportCrash(mainThread, exception)
            }
        }
    }

    fun stopMonitoring() {
        isRunning = false
        interrupt()
    }

    class ANRException(stackTrace: Array<StackTraceElement>) : Exception("ANR Detected: Main thread non-responsive") {
        init {
            setStackTrace(stackTrace)
        }
    }
}
