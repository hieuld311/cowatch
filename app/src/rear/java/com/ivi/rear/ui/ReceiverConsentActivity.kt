package com.ivi.rear.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivi.common.ipc.ShareProtocol
import com.ivi.common.ui.CoWatchTheme
import com.ivi.rear.sharing.RearShareClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReceiverConsentActivity : ComponentActivity() {
    @Inject lateinit var shareClient: RearShareClient
    private val isBootstrapConsent: Boolean
        get() = intent.getBooleanExtra(ShareProtocol.EXTRA_CONSENT_BOOTSTRAP, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContent {
            CoWatchTheme {
                val request by shareClient.pendingShareRequest.collectAsStateWithLifecycle()
                val sharedSession by shareClient.sharedSession.collectAsStateWithLifecycle()
                var requestWasShown by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    display?.displayId?.let(shareClient::markReceiverUiReady)
                }
                LaunchedEffect(request?.snapshot?.sessionId, sharedSession?.sessionId) {
                    if (request != null) requestWasShown = true

                    if (sharedSession != null || (requestWasShown && request == null)) finish()
                }

                request?.let {
                    ReceiverBroadcastDialog(
                        request = it,
                        onShown = shareClient::onPendingRequestDialogShown,
                        onDismiss = {
                            if (isBootstrapConsent) {
                                shareClient.dismissBootstrapRequest(::terminateBootstrapProcess)
                            } else {
                                shareClient.dismissPendingRequest()
                            }
                        },
                        onAccept = shareClient::acceptPendingRequest
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        display?.displayId?.let(shareClient::updateDisplay)
    }

    private fun terminateBootstrapProcess() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }
}
