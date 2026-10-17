package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.scan.LabelTextParser
import com.medishelf.core.model.RecognizedLine
import org.junit.Test
import java.time.LocalDate

class LabelTextParserTest {
    private val today = LocalDate.of(2026, 1, 15)
    private fun parse(vararg lines: Pair<String, Int>) =
        LabelTextParser.parse(lines.map { RecognizedLine(it.first, it.second) }, today)

    @Test
    fun typicalBox_namesStrengthExpiryBatch() {
        val result = parse(
            "PANADOL" to 90,
            "Paracetamol 500 mg" to 40,
            "24 film-coated tablets" to 30,
            "MFG: 03/2025" to 20,
            "EXP: 02/2028" to 20,
            "LOT: B7781X" to 20,
        )
        assertThat(result.names.first()).isEqualTo("Panadol")
        assertThat(result.strengths).containsExactly("500 mg")
        assertThat(result.expiryDates.first()).isEqualTo(LocalDate.of(2028, 2, 29))
        assertThat(result.batchNumbers).containsExactly("B7781X")
    }

    @Test
    fun expiryOnLineAfterKeyword_andManufactureDateRankedLast() {
        val result = parse("Mfd. 12.04.2025" to 20, "Exp. Date" to 20, "11.04.2028" to 20)
        assertThat(result.expiryDates.first()).isEqualTo(LocalDate.of(2028, 4, 11))
        assertThat(result.expiryDates.last()).isEqualTo(LocalDate.of(2025, 4, 12))
    }

    @Test
    fun monthNameFormats() {
        assertThat(parse("EXP MAR 2027" to 20).expiryDates.first()).isEqualTo(LocalDate.of(2027, 3, 31))
        assertThat(parse("USE BY 05-SEP-27" to 20).expiryDates.first()).isEqualTo(LocalDate.of(2027, 9, 5))
        assertThat(parse("EXP 2027-06" to 20).expiryDates.first()).isEqualTo(LocalDate.of(2027, 6, 30))
    }

    @Test
    fun bothDatesOnOneLine_laterIsExpiry() {
        val result = parse("MFG 01/2025 EXP 12/2027" to 20)
        assertThat(result.expiryDates.first()).isEqualTo(LocalDate.of(2027, 12, 31))
    }

    @Test
    fun implausibleDatesAndNoiseIgnored() {
        val result = parse("EXP 01/1980" to 20, "Keep out of reach of children" to 50, "Batch No. ABC" to 20)
        assertThat(result.expiryDates).isEmpty()
        assertThat(result.names).isEmpty()
        assertThat(result.batchNumbers).isEmpty() // no digit = likely a word, not a batch code
    }

    @Test
    fun strengthVariants() {
        val result = parse("Amoxicillin 250mg/5ml" to 30, "Insulin 100 IU/ml" to 30)
        assertThat(result.strengths).containsExactly("250 mg/5 ml", "100 IU/ml")
    }

    @Test
    fun emptyInput() {
        assertThat(parse().isEmpty).isTrue()
    }
}
