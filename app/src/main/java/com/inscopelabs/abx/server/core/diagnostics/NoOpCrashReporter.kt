package com.inscopelabs.abx.server.core.diagnostics

class NoOpCrashReporter : CrashReporter {
    override fun initialize() {}
    override fun reportCrash(thread: Thread, throwable: Throwable) {}
    override fun setEnabled(enabled: Boolean) {}
}
