package io.github.codenextdoor.wealth.testutil

/** Formatted amounts use non-breaking spaces (so "CHF 5" never wraps); tests compare them as plain spaces. */
fun String.withPlainSpaces(): String = replace(' ', ' ').replace(' ', ' ')
