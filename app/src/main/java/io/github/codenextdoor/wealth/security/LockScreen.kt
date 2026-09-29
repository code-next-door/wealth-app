package io.github.codenextdoor.wealth.security

import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.codenextdoor.wealth.R
import io.github.codenextdoor.wealth.ui.theme.heroBrush
import io.github.codenextdoor.wealth.ui.theme.onHeroColor
import kotlinx.coroutines.delay

/** Maximum PIN length; the minimum is [MIN_PIN_LENGTH]. */
const val MAX_PIN_LENGTH = 8
const val MIN_PIN_LENGTH = 4

/**
 * Full-screen lock. The PIN is checked when the user taps OK (not on each
 * digit, so the PIN length isn't revealed). Biometric unlock is offered
 * automatically when enabled.
 */
@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    onPin: (String) -> Boolean,
    secondsUntilNextAttempt: () -> Long,
    onBiometric: () -> Unit,
    /** The phone's key for the PIN is gone: no PIN can be checked. */
    pinUnavailable: Boolean = false,
    /** Only when the data can't be opened either: go on to the recovery screen. */
    onOpenRecovery: (() -> Unit)? = null,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    var waitSeconds by rememberSaveable { mutableLongStateOf(secondsUntilNextAttempt()) }
    val onHero = onHeroColor()

    LaunchedEffect(Unit) { if (biometricEnabled) onBiometric() }
    LaunchedEffect(waitSeconds) {
        if (waitSeconds > 0) {
            delay(1000)
            waitSeconds = secondsUntilNextAttempt()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(heroBrush())
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = onHero, modifier = Modifier.size(40.dp))
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                color = onHero,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                when {
                    pinUnavailable -> stringResource(R.string.lock_pin_unavailable)
                    waitSeconds > 0 -> stringResource(R.string.lock_wait, waitSeconds)
                    wrong -> stringResource(R.string.lock_wrong_pin)
                    else -> stringResource(R.string.lock_enter_pin)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onHero.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp),
            )
            PinDots(pin.length, onHero, Modifier.padding(vertical = 24.dp))
            Spacer(Modifier.weight(0.5f))
            PinPad(
                color = onHero,
                enabled = waitSeconds == 0L,
                onDigit = { if (pin.length < MAX_PIN_LENGTH) { pin += it; wrong = false } },
                onDelete = { pin = pin.dropLast(1) },
                onSubmit = {
                    if (pin.length >= MIN_PIN_LENGTH) {
                        val ok = onPin(pin)
                        if (!ok) {
                            wrong = true
                            pin = ""
                            waitSeconds = secondsUntilNextAttempt()
                        }
                    }
                },
            )
            if (biometricEnabled) {
                TextButton(onClick = onBiometric, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.lock_use_biometric), color = onHero)
                }
            }
            onOpenRecovery?.let {
                TextButton(onClick = it, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.lock_open_recovery), color = onHero)
                }
            }
            Spacer(Modifier.weight(0.3f))
        }
    }
}

@Composable
fun PinDots(count: Int, color: Color, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.lock_digits_entered, count, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .height(16.dp)
            .semantics { contentDescription = description },
    ) {
        repeat(count) { Box(Modifier.size(14.dp).background(color, CircleShape)) }
    }
}

/** Numeric keypad: 1-9, delete, 0, OK. */
@Composable
fun PinPad(
    color: Color,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> PadKey(digit.toString(), color, enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(72.dp)) {
                Icon(painterResource(R.drawable.ic_backspace), stringResource(R.string.lock_delete_digit), tint = color)
            }
            PadKey("0", color, enabled) { onDigit('0') }
            TextButton(onClick = onSubmit, enabled = enabled, modifier = Modifier.size(72.dp)) {
                Text(stringResource(R.string.action_ok), color = color, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PadKey(label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color,
        ),
        modifier = Modifier.size(72.dp),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}
