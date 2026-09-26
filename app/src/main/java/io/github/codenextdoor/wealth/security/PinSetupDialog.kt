package io.github.codenextdoor.wealth.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R

/** What the PIN dialog is asking for. */
enum class PinDialogMode { CREATE, CONFIRM_CURRENT }

/**
 * Asks for a PIN on a keypad. In [PinDialogMode.CREATE] it asks twice and
 * calls [onDone] with the new PIN once both match; in CONFIRM_CURRENT it
 * asks once and [onDone] returns whether it was right.
 */
@Composable
fun PinDialog(
    mode: PinDialogMode,
    onDone: (pin: String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    val color = MaterialTheme.colorScheme.onSurface

    val title = when {
        mode == PinDialogMode.CONFIRM_CURRENT -> R.string.pin_enter_current
        first == null -> R.string.pin_choose
        else -> R.string.pin_repeat
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(message ?: R.string.pin_length_hint, MIN_PIN_LENGTH, MAX_PIN_LENGTH),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PinDots(pin.length, color, Modifier.padding(vertical = 16.dp))
                PinPad(
                    color = color,
                    enabled = true,
                    onDigit = { if (pin.length < MAX_PIN_LENGTH) pin += it },
                    onDelete = { pin = pin.dropLast(1) },
                    onSubmit = submit@{
                        if (pin.length < MIN_PIN_LENGTH) return@submit
                        when {
                            mode == PinDialogMode.CONFIRM_CURRENT -> {
                                if (!onDone(pin)) message = R.string.pin_wrong
                                pin = ""
                            }
                            first == null -> {
                                first = pin
                                pin = ""
                                message = null
                            }
                            first == pin -> onDone(pin)
                            else -> {
                                first = null
                                pin = ""
                                message = R.string.pin_mismatch
                            }
                        }
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
