package com.ivi.pid.sharing

import com.ivi.common.ipc.ScreenRole

data class HostShareNotification(
    val type: HostShareNotificationType,
    val role: String?
) {
    val message: String
        get() = buildString {
            append(type.message)
            role?.let { append(" - ").append(it.notificationLabel()) }
        }

    companion object {
        fun forResponse(
            type: HostShareNotificationType,
            role: String,
            requestedTargetCount: Int
        ): HostShareNotification = HostShareNotification(
            type = type,
            role = role.takeIf { requestedTargetCount > 1 }
        )
    }
}

enum class HostShareNotificationType(val message: String) {
    ACCEPTED("Video broadcast accepted"),
    DENIED("Video broadcast denied"),
    CANCELLED("Video broadcast cancelled")
}

private fun String.notificationLabel(): String = when (this) {
    ScreenRole.REAR_LEFT -> "Rear Left"
    ScreenRole.REAR_RIGHT -> "Rear Right"
    else -> this
}
