package io.github.codenextdoor.wealth

import android.content.Context

/**
 * Manual dependency injection: creates and holds single instances of the
 * database and repositories. Populated as features are added.
 */
class AppContainer(@Suppress("unused") private val context: Context)
