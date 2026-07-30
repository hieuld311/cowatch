package com.ivi.pid.sharing

import com.ivi.common.ipc.ScreenRole

internal object RearDisplayConfig {
    fun displayId(role: String): Int? = when (role) {
        ScreenRole.REAR_LEFT -> 2
        ScreenRole.REAR_RIGHT -> 3
        else -> null
    }
}
