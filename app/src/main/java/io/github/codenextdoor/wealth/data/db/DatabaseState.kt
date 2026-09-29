package io.github.codenextdoor.wealth.data.db

/** Whether the encrypted database could be opened at start (see `AppContainer.openDatabase`). */
sealed interface DatabaseState {
    data object Opening : DatabaseState
    data object Ready : DatabaseState

    /** The data can't be opened: the recovery screen offers a backup or a fresh start. */
    data class Unreadable(val reason: Reason) : DatabaseState

    enum class Reason { KEY_MISSING, KEY_UNAVAILABLE, WRONG_KEY }

    companion object {
        /**
         * Only key problems count as unreadable; anything else (e.g. a migration bug)
         * is rethrown and crashes as before, rather than offering to set data aside.
         */
        fun reasonFor(error: Throwable): Reason? = generateSequence(error) { it.cause }.firstNotNullOfOrNull {
            when {
                it is DatabaseKeyManager.MissingKey -> Reason.KEY_MISSING
                it is DatabaseKeyManager.KeyUnavailable -> Reason.KEY_UNAVAILABLE
                // SQLCipher with the wrong key: the file looks like garbage ("file is not a database").
                it.message.orEmpty().contains("file is not a database", ignoreCase = true) -> Reason.WRONG_KEY
                else -> null
            }
        }
    }
}
