package io.github.codenextdoor.wealth.domain

/**
 * Replaces every number in [text] with "••••" (for the eye that hides figures):
 * grouping, decimals and a short-form suffix go with it ("₹12.5L" -> "₹••••"),
 * while the currency, sign and "%" stay. Always the same length, so the mask
 * doesn't give away how big the number is.
 */
fun maskFigures(text: String): String = NUMBER.replace(text, MASK)

private const val MASK = "••••"

// A digit, then digits and separators (’ ' , . and non-breaking spaces) ending in a digit,
// then an optional short-form suffix (K, M, L, Cr, Mio. …) that isn't the start of a word.
private val NUMBER = Regex("""\d(?:[\d.,’'  ]*\d)?(?:[  ]?(?:Mio\.|Mrd\.|Bio\.|Cr|K|M|B|T|L|k)(?!\p{L}))?""")
