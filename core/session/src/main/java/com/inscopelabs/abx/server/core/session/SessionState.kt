package com.inscopelabs.abx.server.core.session

sealed class SessionState {
    object INACTIVE : SessionState()
    object ACTIVE : SessionState()
    object EXPIRED : SessionState()
    object REVOKED : SessionState()
}
