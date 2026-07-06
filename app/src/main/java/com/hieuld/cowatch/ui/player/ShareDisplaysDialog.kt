package com.hieuld.cowatch.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hieuld.cowatch.domain.display.DisplayInfo

@Composable
internal fun ShareDisplaysDialog(
    displays: List<DisplayInfo>,
    onDismiss: () -> Unit,
    onStartSharing: (Set<Int>) -> Unit
) {
    var selectedDisplayIds by remember(displays) { mutableStateOf(emptySet<Int>()) }
    val allSelected = displays.isNotEmpty() && selectedDisplayIds.size == displays.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Share media playback",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                SelectableDisplayRow(
                    title = "All secondary displays",
                    subtitle = "${displays.size} available",
                    checked = allSelected,
                    enabled = displays.isNotEmpty(),
                    onCheckedChange = { checked ->
                        selectedDisplayIds = if (checked) {
                            displays.map { it.displayId }.toSet()
                        } else {
                            emptySet()
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = DividerDefaults.color.copy(alpha = 0.45f)
                )

                if (displays.isEmpty()) {
                    Text(
                        text = "No secondary display found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.35f)
                    ) {
                        items(displays, key = { it.displayId }) { display ->
                            SelectableDisplayRow(
                                title = display.name,
                                subtitle = "Display id ${display.displayId}",
                                checked = selectedDisplayIds.contains(display.displayId),
                                enabled = true,
                                onCheckedChange = { checked ->
                                    selectedDisplayIds = if (checked) {
                                        selectedDisplayIds + display.displayId
                                    } else {
                                        selectedDisplayIds - display.displayId
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedDisplayIds.isNotEmpty(),
                onClick = { onStartSharing(selectedDisplayIds) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Start",
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        // 8dp radius keeps the dialog aligned with the compact operational UI style.
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun SelectableDisplayRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 56dp row height gives each display target a stable automotive touch footprint.
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                text = title,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                },
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
