package com.medishelf.core.ui

import androidx.lifecycle.SavedStateHandle
import com.medishelf.core.model.ScanSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Hands a scan result back to the screen that opened the scanner, via that destination's
 * SavedStateHandle (survives process death). Each result is delivered once, then cleared.
 */
object ScanResultTransfer {
    private const val KEY = "medishelf.scan_result"

    fun put(handle: SavedStateHandle, result: ScanSuggestion) {
        handle[KEY] = arrayListOf(
            result.barcode,
            result.name,
            result.strength,
            result.expiryDate?.toString(),
            result.batchNumber,
        )
    }

    fun results(handle: SavedStateHandle): Flow<ScanSuggestion> =
        handle.getStateFlow<ArrayList<String?>?>(KEY, null)
            .filterNotNull()
            .map { values ->
                handle[KEY] = null
                ScanSuggestion(
                    barcode = values.getOrNull(0),
                    name = values.getOrNull(1),
                    strength = values.getOrNull(2),
                    expiryDate = values.getOrNull(3)?.let(LocalDate::parse),
                    batchNumber = values.getOrNull(4),
                )
            }
}
