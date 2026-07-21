package com.inscopelabs.abx.server.workspace.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ChatDownloadManager {
    fun downloadArtifact(url: String): Flow<Float> = flow {
        emit(0.0f)
        for (i in 1..5) {
            delay(200)
            emit(i * 0.2f)
        }
    }
}