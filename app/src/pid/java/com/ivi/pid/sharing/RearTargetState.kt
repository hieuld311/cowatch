package com.ivi.pid.sharing

import com.ivi.common.ipc.ReceiverState
import com.ivi.common.ipc.ScreenRole

data class RearTargetState(
    val role: String,
    val name: String = ScreenRole.label(role),
    val displayId: Int? = null,
    val state: String = ReceiverState.NOT_RUNNING,
    val errorMessage: String? = null
) {
    val available: Boolean get() = displayId != null
    val selectable: Boolean
        get() = available &&
            (state == ReceiverState.NOT_RUNNING || state == ReceiverState.READY)
}
