package com.medishelf.core.ui

import androidx.lifecycle.SavedStateHandle
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.model.ScanSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Hands a scan result to an editor through its SavedStateHandle (survives process death).
 * Each result is delivered once, then cleared. Stored as a list of strings in fixed positions
 * so it fits in a Bundle without Parcelable plumbing.
 */
object ScanResultTransfer {
    private const val KEY = "medishelf.scan_result"

    fun put(handle: SavedStateHandle, result: ScanSuggestion) {
        handle[KEY] = arrayListOf(
            result.barcode,
            result.name,
            result.genericName,
            result.strength,
            result.form?.name,
            result.quantity?.toString(),
            result.unit?.name,
            result.manufacturer,
            result.expiryDate?.toString(),
            result.batchNumber,
            result.isPrescription?.toString(),
            result.dosageInstructions,
            result.storageNote,
        )
    }

    fun results(handle: SavedStateHandle): Flow<ScanSuggestion> =
        handle.getStateFlow<ArrayList<String?>?>(KEY, null)
            .filterNotNull()
            .map { v ->
                handle[KEY] = null
                ScanSuggestion(
                    barcode = v.getOrNull(0),
                    name = v.getOrNull(1),
                    genericName = v.getOrNull(2),
                    strength = v.getOrNull(3),
                    form = v.getOrNull(4)?.let { name -> MedicineForm.entries.find { it.name == name } },
                    quantity = v.getOrNull(5)?.toDoubleOrNull(),
                    unit = v.getOrNull(6)?.let { name -> QuantityUnit.entries.find { it.name == name } },
                    manufacturer = v.getOrNull(7),
                    expiryDate = v.getOrNull(8)?.let(LocalDate::parse),
                    batchNumber = v.getOrNull(9),
                    isPrescription = v.getOrNull(10)?.toBooleanStrictOrNull(),
                    dosageInstructions = v.getOrNull(11),
                    storageNote = v.getOrNull(12),
                )
            }
}
