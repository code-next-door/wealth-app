package io.github.codenextdoor.wealth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.codenextdoor.wealth.AppContainer
import io.github.codenextdoor.wealth.WealthApplication

/**
 * Builds a ViewModel factory that gets its dependencies from [AppContainer].
 * Usage: `viewModel(factory = appViewModelFactory { MyViewModel(it.someRepository) })`
 */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer) -> VM,
) = viewModelFactory {
    initializer { create((this[APPLICATION_KEY] as WealthApplication).container) }
}

/**
 * Like [appViewModelFactory], plus a [SavedStateHandle] holding the screen's
 * navigation arguments (e.g. which account to edit).
 */
inline fun <reified VM : ViewModel> appViewModelFactoryWithState(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
) = viewModelFactory {
    initializer {
        create((this[APPLICATION_KEY] as WealthApplication).container, createSavedStateHandle())
    }
}
