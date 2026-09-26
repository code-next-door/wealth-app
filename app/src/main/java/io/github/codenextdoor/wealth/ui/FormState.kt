package io.github.codenextdoor.wealth.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds what the user is typing, in Compose state rather than a Flow.
 *
 * Text fields must see each change synchronously: routing keystrokes through
 * an asynchronous Flow and back makes fast typing drop or reorder characters.
 * Composables that read [value] recompose when it changes.
 */
class FormState<T>(initial: T) {
    var value: T by mutableStateOf(initial)

    fun update(transform: (T) -> T) {
        value = transform(value)
    }
}
