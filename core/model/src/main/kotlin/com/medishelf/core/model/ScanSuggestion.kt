package com.medishelf.core.model

import java.time.LocalDate

/**
 * Values read from a pack by the camera. Always suggestions: the editor marks them for the
 * user to check, and nothing is saved until the user confirms.
 */
data class ScanSuggestion(
    val barcode: String? = null,
    val name: String? = null,
    val genericName: String? = null,
    val strength: String? = null,
    val form: MedicineForm? = null,
    val quantity: Double? = null,
    val unit: QuantityUnit? = null,
    val manufacturer: String? = null,
    val expiryDate: LocalDate? = null,
    val batchNumber: String? = null,
    /** True when the pack is marked prescription-only; null when nothing was found. */
    val isPrescription: Boolean? = null,
    val dosageInstructions: String? = null,
    val storageNote: String? = null,
    /** "Use within 28 days of opening" -> 28. */
    val useWithinDays: Int? = null,
    /** From a pharmacy dispensing label. */
    val prescribedBy: String? = null,
    val prescriptionDate: LocalDate? = null,
    val patientName: String? = null,
    val condition: String? = null,
) {
    val isEmpty: Boolean
        get() = this == ScanSuggestion()
}

/** Pack contents, e.g. 24 tablets or 100 ml. */
data class PackQuantity(val amount: Double, val unit: QuantityUnit)

/** Ranked candidates read from one or more photos of a pack, best first. */
data class LabelSuggestions(
    val names: List<String> = emptyList(),
    val genericNames: List<String> = emptyList(),
    val strengths: List<String> = emptyList(),
    val forms: List<MedicineForm> = emptyList(),
    val quantities: List<PackQuantity> = emptyList(),
    val manufacturers: List<String> = emptyList(),
    val expiryDates: List<LocalDate> = emptyList(),
    val batchNumbers: List<String> = emptyList(),
    val isPrescription: Boolean = false,
    val dosageInstructions: List<String> = emptyList(),
    val storageNotes: List<String> = emptyList(),
    val useWithinDays: List<Int> = emptyList(),
    val prescribers: List<String> = emptyList(),
    val prescriptionDates: List<LocalDate> = emptyList(),
    val patientNames: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    /** Normalised barcode found in the photos or live preview, if any. */
    val barcode: String? = null,
    /** Everything the camera read, for the user to check against. */
    val rawText: String = "",
) {
    val isEmpty: Boolean
        get() = copy(rawText = "") == LabelSuggestions()
}

/** One line of recognised text; [height] (px) ranks name candidates, as product names print largest. */
data class RecognizedLine(val text: String, val height: Int)
