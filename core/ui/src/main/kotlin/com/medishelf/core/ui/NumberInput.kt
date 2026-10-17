package com.medishelf.core.ui

/**
 * Parses user-typed numbers, accepting "." or "," as the decimal separator (whichever the
 * keyboard offers). Returns null for blank or invalid input.
 */
fun parseLocalizedNumber(text: String): Double? {
    val normalized = text.trim().replace(',', '.')
    if (normalized.isEmpty() || normalized.count { it == '.' } > 1) return null
    if (!normalized.all { it.isAsciiDigitOrDot() }) return null
    return normalized.toDoubleOrNull()?.takeUnless { it.isNaN() || it.isInfinite() }
}

private fun Char.isAsciiDigitOrDot() = this in '0'..'9' || this == '.'

fun parseLocalizedInt(text: String): Int? =
    parseLocalizedNumber(text)?.takeIf { it == Math.floor(it) && it <= Int.MAX_VALUE && it >= Int.MIN_VALUE }?.toInt()

/** Quantity as an editable string: "10" not "10.0". */
fun Double.toEditableString(): String = if (this == Math.floor(this) && !isInfinite()) toLong().toString() else toString()
