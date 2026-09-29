package io.github.codenextdoor.wealth.ui.tour

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The app tour: which of [TourSteps] is showing, or null. Shared app-wide so
 * the welcome screen and Settings › Help can start it and the home screen shows
 * it. It only points at things; it never taps or changes anything.
 */
class Tour(private val stepCount: Int = TourSteps.all.size) {

    private val _step = MutableStateFlow<Int?>(null)
    val step: StateFlow<Int?> = _step.asStateFlow()

    fun start() {
        _step.value = 0
    }

    /** The next stop, or the end after the last. */
    fun next() = _step.update { current -> current?.let { if (it + 1 < stepCount) it + 1 else null } }

    fun stop() {
        _step.value = null
    }
}
