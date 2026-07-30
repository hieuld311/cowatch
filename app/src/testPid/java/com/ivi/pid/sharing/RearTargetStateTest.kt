package com.ivi.pid.sharing

import com.ivi.common.ipc.ReceiverState
import com.ivi.common.ipc.ScreenRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RearTargetStateTest {
    @Test
    fun configuredDisplayIdsMatchTheVehicleLayout() {
        assertEquals(2, RearDisplayConfig.displayId(ScreenRole.REAR_LEFT))
        assertEquals(3, RearDisplayConfig.displayId(ScreenRole.REAR_RIGHT))
    }

    @Test
    fun availableDisplayIsSelectableWhenRearAppIsNotRunning() {
        val target = RearTargetState(
            role = ScreenRole.REAR_LEFT,
            displayId = 2,
            state = ReceiverState.NOT_RUNNING
        )

        assertTrue(target.available)
        assertTrue(target.selectable)
    }

    @Test
    fun unavailableDisplayIsNotSelectable() {
        val target = RearTargetState(
            role = ScreenRole.REAR_LEFT,
            displayId = null,
            state = ReceiverState.NOT_RUNNING
        )

        assertFalse(target.available)
        assertFalse(target.selectable)
    }

    @Test
    fun startingRearAppCannotBeSelectedAgain() {
        val target = RearTargetState(
            role = ScreenRole.REAR_RIGHT,
            displayId = 3,
            state = ReceiverState.STARTING
        )

        assertTrue(target.available)
        assertFalse(target.selectable)
    }

    @Test
    fun readyRearAppRemainsSelectable() {
        val target = RearTargetState(
            role = ScreenRole.REAR_RIGHT,
            displayId = 3,
            state = ReceiverState.READY
        )

        assertTrue(target.selectable)
    }

    @Test
    fun failedRearTargetIsNotSelectable() {
        val target = RearTargetState(
            role = ScreenRole.REAR_LEFT,
            displayId = 2,
            state = ReceiverState.FAILED
        )

        assertFalse(target.selectable)
    }
}
