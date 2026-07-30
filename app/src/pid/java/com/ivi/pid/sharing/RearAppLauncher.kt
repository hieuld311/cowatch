package com.ivi.pid.sharing

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.ivi.common.ipc.ScreenRole
import com.ivi.common.ipc.ShareProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RearAppLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun launch(role: String, displayId: Int): Result<Unit> = runCatching {
        val intent = Intent()
            .setComponent(
                ComponentName(
                    ScreenRole.packageName(role),
                    REAR_CONSENT_ACTIVITY_CLASS
                )
            )
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtra(ShareProtocol.EXTRA_CONSENT_BOOTSTRAP, true)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        context.startActivity(intent, options.toBundle())
    }

    private companion object {
        const val REAR_CONSENT_ACTIVITY_CLASS = "com.ivi.rear.ui.ReceiverConsentActivity"
    }
}
