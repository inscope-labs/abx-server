package com.inscopelabs.abx.server.boot

import android.content.Context

object BootGuard {

    fun stageStart(stage: String) {
        // no-op
    }

    fun stageSuccess(stage: String) {
        // no-op
    }

    fun recordFailure(context: Context?, stage: String, throwable: Throwable) {
        // no-op
    }

    fun hasFailure(context: Context?): Boolean {
        return false
    }

    fun currentFailure(context: Context?): Failure? {
        return null
    }

    fun clear(context: Context?) {
        // no-op
    }

    data class Failure(
        val stage: String,
        val message: String,
        val stackTrace: String,
        val timestamp: String
    )
}
