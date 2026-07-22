package com.inscopelabs.abx.server.workspace

enum class SecureTab {
    CONNECT,
    ACCESS,
    REMOVE,
    ACTIVITY
}

interface SecureNavigation {
    fun openSecureTab(tab: SecureTab)
}
