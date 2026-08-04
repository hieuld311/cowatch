package com.ivi.rear.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivi.rear.sharing.PendingShareRequest
import com.ivi.common.ui.coWatchColorScheme
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
internal fun ReceiverBroadcastDialog(
    request: PendingShareRequest,
    onShown: (String) -> Unit,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.coWatchColorScheme
    var remainingSeconds by remember(request.snapshot.sessionId) {
        mutableIntStateOf(request.remainingSeconds())
    }

    LaunchedEffect(request.snapshot.sessionId) {
        withFrameNanos { }
        onShown(request.snapshot.sessionId)
    }

    LaunchedEffect(request.snapshot.sessionId, request.deadlineElapsedRealtimeMs) {
        if (request.deadlineElapsedRealtimeMs == null) return@LaunchedEffect
        remainingSeconds = request.remainingSeconds()
        while (remainingSeconds > 0) {
            delay(COUNTDOWN_REFRESH_MS)
            remainingSeconds = request.remainingSeconds()
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .width(with(density) { DIALOG_WIDTH_PX.toDp() })
                .height(with(density) { DIALOG_HEIGHT_PX.toDp() })
                .background(colors.dialogSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { DIALOG_HEADER_HEIGHT_PX.toDp() }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Accept video broadcast request?",
                    color = colors.contentPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = colors.dialogDivider)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { DIALOG_CONTENT_HEIGHT_PX.toDp() }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = request.snapshot.title,
                    color = colors.dialogAccent,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
                Spacer(modifier = Modifier.height(with(density) { TITLE_COUNTDOWN_GAP_PX.toDp() }))
                Text(
                    text = "Accepting the video broadcast in $remainingSeconds seconds",
                    color = colors.dialogNormalText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }

            HorizontalDivider(color = colors.dialogDivider)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = with(density) { ACTION_HORIZONTAL_PADDING_PX.toDp() }),
                horizontalArrangement = Arrangement.spacedBy(
                    with(density) { ACTION_GAP_PX.toDp() }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReceiverDialogButton(
                    text = "Dismiss",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                ReceiverDialogButton(
                    text = "Accept",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReceiverDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.coWatchColorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(with(density) { ACTION_HEIGHT_PX.toDp() }),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.dialogAction,
            contentColor = colors.dialogActionContent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.headlineSmall)
    }
}

private fun PendingShareRequest.remainingSeconds(): Int {
    val deadline = deadlineElapsedRealtimeMs
        ?: return ceil(countdownDurationMs / 1_000.0).toInt()
    val remainingMs = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    return ceil(remainingMs / 1_000.0).toInt()
}

private const val COUNTDOWN_REFRESH_MS = 250L
private const val DIALOG_WIDTH_PX = 888f
private const val DIALOG_HEIGHT_PX = 412f
private const val DIALOG_HEADER_HEIGHT_PX = 96f
private const val DIALOG_CONTENT_HEIGHT_PX = 140f
private const val TITLE_COUNTDOWN_GAP_PX = 22f
private const val ACTION_HORIZONTAL_PADDING_PX = 50f
private const val ACTION_GAP_PX = 20f
private const val ACTION_HEIGHT_PX = 84f
