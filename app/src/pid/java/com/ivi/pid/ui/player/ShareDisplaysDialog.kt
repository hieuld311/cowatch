package com.ivi.pid.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivi.R
import com.ivi.pid.sharing.RearTargetState

@Composable
internal fun ShareDisplaysDialog(
    displays: List<RearTargetState>,
    onDismiss: () -> Unit,
    onStartSharing: (Set<String>) -> Unit
) {
    var selectedRoles by remember(displays) { mutableStateOf(emptySet<String>()) }
    val density = LocalDensity.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(with(density) { DIALOG_WIDTH_PX.toDp() })
                .height(with(density) { DIALOG_HEIGHT_PX.toDp() })
                .background(DialogBackgroundColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { DIALOG_HEADER_HEIGHT_PX.toDp() }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Accept video broadcast request?",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = DialogDividerColor)

            if (displays.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No secondary display found",
                        color = DisabledTextColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(
                            horizontal = with(density) { DIALOG_LIST_HORIZONTAL_PADDING_PX.toDp() }
                        ),
                    contentPadding = PaddingValues(
                        vertical = with(density) { DIALOG_LIST_VERTICAL_PADDING_PX.toDp() }
                    )
                ) {
                    items(displays, key = { it.role }) { display ->
                        val checked = display.role in selectedRoles
                        val enabled = display.selectable
                        DisplayOptionRow(
                            display = display,
                            checked = checked,
                            enabled = enabled,
                            onToggle = {
                                selectedRoles = if (checked) {
                                    selectedRoles - display.role
                                } else {
                                    selectedRoles + display.role
                                }
                            }
                        )
                        HorizontalDivider(color = DialogDividerColor)
                    }
                }
            }

            DialogActions(
                broadcastEnabled = selectedRoles.isNotEmpty(),
                onDismiss = onDismiss,
                onBroadcast = { onStartSharing(selectedRoles) }
            )
        }
    }
}

@Composable
private fun DisplayOptionRow(
    display: RearTargetState,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val selectedBackground = painterResource(R.drawable.btn_general_list_vertical_s)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { DISPLAY_ROW_HEIGHT_PX.toDp() })
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onToggle
            )
    ) {
        if (checked) {
            Image(
                painter = selectedBackground,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(with(density) { SELECTED_BACKGROUND_WIDTH_PX.toDp() })
                    .height(with(density) { SELECTED_BACKGROUND_HEIGHT_PX.toDp() }),
                contentScale = ContentScale.Fit
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = with(density) { DISPLAY_ROW_CONTENT_PADDING_PX.toDp() }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(
                    checkboxDrawable(
                        checked = checked,
                        pressed = pressed,
                        enabled = enabled
                    )
                ),
                contentDescription = if (checked) {
                    "Deselect ${display.name}"
                } else {
                    "Select ${display.name}"
                },
                modifier = Modifier.size(with(density) { CHECKBOX_SIZE_PX.toDp() })
            )
            Text(
                text = "${display.name}  •  ${display.state.lowercase().replace('_', ' ')}",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = with(density) { DISPLAY_TEXT_START_PADDING_PX.toDp() }),
                color = when {
                    !enabled -> DisabledTextColor
                    checked -> SelectedTextColor
                    else -> NormalTextColor
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DialogActions(
    broadcastEnabled: Boolean,
    onDismiss: () -> Unit,
    onBroadcast: () -> Unit
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = with(density) { DIALOG_ACTION_HORIZONTAL_PADDING_PX.toDp() },
                end = with(density) { DIALOG_ACTION_HORIZONTAL_PADDING_PX.toDp() },
                bottom = with(density) { DIALOG_ACTION_BOTTOM_PADDING_PX.toDp() }
            ),
        horizontalArrangement = Arrangement.spacedBy(
            with(density) { DIALOG_ACTION_GAP_PX.toDp() }
        )
    ) {
        DialogButton(
            text = "Cancel",
            enabled = true,
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        )
        DialogButton(
            text = "Broadcast",
            enabled = broadcastEnabled,
            onClick = onBroadcast,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DialogButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(with(density) { DIALOG_ACTION_HEIGHT_PX.toDp() }),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionButtonColor,
            contentColor = Color.White,
            disabledContainerColor = DisabledActionButtonColor,
            disabledContentColor = DisabledTextColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@DrawableRes
private fun checkboxDrawable(
    checked: Boolean,
    pressed: Boolean,
    enabled: Boolean
): Int {
    return when {
        checked && !enabled -> R.drawable.btn_general_checkbox_on_d
        checked && pressed -> R.drawable.btn_general_checkbox_on_p
        checked -> R.drawable.btn_general_checkbox_on_s
        !enabled -> R.drawable.btn_general_checkbox_off_d
        pressed -> R.drawable.btn_general_checkbox_off_p
        else -> R.drawable.btn_general_checkbox_off_n
    }
}

private const val DIALOG_WIDTH_PX = 992f
private const val DIALOG_HEIGHT_PX = 653f
private const val DIALOG_HEADER_HEIGHT_PX = 110f
private const val DIALOG_LIST_HORIZONTAL_PADDING_PX = 30f
private const val DIALOG_LIST_VERTICAL_PADDING_PX = 30f
private const val DISPLAY_ROW_HEIGHT_PX = 150f
private const val SELECTED_BACKGROUND_WIDTH_PX = 150f
private const val SELECTED_BACKGROUND_HEIGHT_PX = 151f
private const val DISPLAY_ROW_CONTENT_PADDING_PX = 50f
private const val DISPLAY_TEXT_START_PADDING_PX = 28f
private const val CHECKBOX_SIZE_PX = 48f
private const val DIALOG_ACTION_HORIZONTAL_PADDING_PX = 50f
private const val DIALOG_ACTION_BOTTOM_PADDING_PX = 24f
private const val DIALOG_ACTION_GAP_PX = 20f
private const val DIALOG_ACTION_HEIGHT_PX = 84f

private val DialogBackgroundColor = Color(0xFF25263B)
private val DialogDividerColor = Color.White.copy(alpha = 0.08f)
private val SelectedTextColor = Color(0xFF00F9EC)
private val NormalTextColor = Color(0xFFC7CADA)
private val DisabledTextColor = Color(0xFF838497)
private val ActionButtonColor = Color(0xFF62636F)
private val DisabledActionButtonColor = Color(0xFF4A4B56)
