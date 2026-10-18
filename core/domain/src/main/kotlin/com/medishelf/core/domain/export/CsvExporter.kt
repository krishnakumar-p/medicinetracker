package com.medishelf.core.domain.export

import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.QuantityUnit
import java.time.LocalDate

/** Localised column titles and enum labels, resolved by the UI layer. */
data class CsvLabels(
    val headers: List<String>,
    val forms: Map<MedicineForm, String>,
    val units: Map<QuantityUnit, String>,
    val statuses: Map<MedicineStatus, String>,
    val yes: String,
    val no: String,
) {
    init {
        require(headers.size == CsvExporter.COLUMN_COUNT) { "Expected ${CsvExporter.COLUMN_COUNT} headers" }
    }
}

/**
 * Inventory as CSV for sharing with a doctor. RFC 4180 quoting, UTF-8 with BOM so Excel shows
 * accented names and symbols correctly, and spreadsheet-formula neutralising (CWE-1236) since names are user input.
 */
object CsvExporter {
    const val COLUMN_COUNT = 18

    fun export(items: List<MedicineDetails>, today: LocalDate, labels: CsvLabels, memberId: Long? = null): String =
        buildString {
            append('﻿')
            appendRow(labels.headers)
            items.forEach { details ->
                val m = details.medicine
                val people = details.assignments
                    .filter { memberId == null || it.member.id == memberId }
                    .joinToString("; ") { a -> listOfNotNull(a.member.name, a.condition).joinToString(": ") }
                appendRow(
                    listOf(
                        m.brandName,
                        m.genericName,
                        m.strength,
                        labels.forms.getValue(m.form),
                        quantity(m.quantity),
                        labels.units.getValue(m.unit),
                        m.effectiveExpiryDate.toString(),
                        labels.statuses.getValue(m.effectiveStatus(today)),
                        people,
                        m.dosageInstructions,
                        m.prescribedBy,
                        listOfNotNull(details.prescribedFor?.name, m.prescribedForReason.takeIf(String::isNotBlank)).joinToString(": "),
                        m.prescriptionStartDate?.toString().orEmpty(),
                        m.prescriptionEndDate?.toString().orEmpty(),
                        if (m.isPrescription) labels.yes else labels.no,
                        details.location?.name.orEmpty(),
                        m.batchNumber,
                        m.notes,
                    ),
                )
            }
        }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, ",") { escape(it) }
        append("\r\n")
    }

    internal fun escape(raw: String): String {
        // A leading = + - @ (or tab/CR) would make spreadsheets evaluate the cell as a formula.
        val safe = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private fun quantity(value: Double) =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()
}
