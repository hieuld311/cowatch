package com.ivi.pid.sharing

import com.ivi.common.ipc.ScreenRole
import org.junit.Assert.assertEquals
import org.junit.Test

class HostShareNotificationTest {
    @Test
    fun singleTargetDoesNotAppendRearRole() {
        val notification = HostShareNotification.forResponse(
            type = HostShareNotificationType.ACCEPTED,
            role = ScreenRole.REAR_LEFT,
            requestedTargetCount = 1
        )

        assertEquals("Video broadcast accepted", notification.message)
    }

    @Test
    fun twoTargetsAppendRearLeftRole() {
        val notification = HostShareNotification.forResponse(
            type = HostShareNotificationType.DENIED,
            role = ScreenRole.REAR_LEFT,
            requestedTargetCount = 2
        )

        assertEquals("Video broadcast denied - Rear Left", notification.message)
    }

    @Test
    fun twoTargetsAppendRearRightRole() {
        val notification = HostShareNotification.forResponse(
            type = HostShareNotificationType.CANCELLED,
            role = ScreenRole.REAR_RIGHT,
            requestedTargetCount = 2
        )

        assertEquals("Video broadcast cancelled - Rear Right", notification.message)
    }
}
