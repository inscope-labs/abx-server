package com.inscopelabs.abx.server.core.session

sealed class UserGesture {
    object LocalButtonPress : UserGesture()
    object NotificationAction : UserGesture()
}
