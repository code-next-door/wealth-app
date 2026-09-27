package io.github.codenextdoor.wealth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds a form's non-text choices (type, currency, date, ...) in Compose
 * state, so the screen's derived state updates immediately. Typed text lives
 * in Compose's TextFieldState instead. Composables that read [value]
 * recompose when it changes.
 */
class FormState<T>(initial: T) {
    var value: T by mutableStateOf(initial)

    fun update(transform: (T) -> T) {
        value = transform(value)
    }
}
