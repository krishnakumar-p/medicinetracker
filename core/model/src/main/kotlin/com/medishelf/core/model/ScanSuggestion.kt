package com.medishelf.core.model

import java.time.LocalDate

/**
 * Values read from a pack by the camera. Always suggestions: the editor marks them for the
 * user to check, and nothing is saved until the user confirms.
 */
data class ScanSuggestion(
    val barcode: String? = null,
    val name: String? = null,
    val strength: String? = null,
    val expiryDate: LocalDate? = null,
    val batchNumber: String? = null,
) {
    val isEmpty: Boolean
        get() = barcode == null && name == null && strength == null && expiryDate == null && batchNumber == null
}

/** Ranked candidates read from a label photo, best first. */
data class LabelSuggestions(
    val names: List<String> = emptyList(),
    val strengths: List<String> = emptyList(),
    val expiryDates: List<LocalDate> = emptyList(),
    val batchNumbers: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = names.isEmpty() && strengths.isEmpty() && expiryDates.isEmpty() && batchNumbers.isEmpty()
}

/** One line of recognised text; [height] (px) ranks name candidates, as product names print largest. */
data class RecognizedLine(val text: String, val height: Int)
