package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.scan.LabelTextParser
import com.medishelf.core.model.RecognizedLine
import org.junit.Test
import java.time.LocalDate

/** Layouts the keyword rules alone miss, which the lexicon-based recogniser catches. */
class NlpLabelParserTest {
    private val today = LocalDate.of(2026, 1, 15)
    private fun parse(vararg lines: Pair<String, Int>) =
        LabelTextParser.parse(lines.map { RecognizedLine(it.first, it.second) }, today)

    @Test
    fun noKeywords_ocrTypos_combinationProduct() {
        val result = parse(
            "PANADOL" to 90,
            "Extra" to 40,
            "PARACETAM0L 500MG + CAFFEINE 65MG" to 22, // OCR read O as zero
            "20 caplets" to 18,
            "Excipients: maize starch, povidone, talc" to 12,
            "GSK" to 14,
        )
        assertThat(result.genericNames.first()).isEqualTo("Paracetamol + Caffeine")
        assertThat(result.genericNames).doesNotContain("Povidone")
        assertThat(result.strengths).contains("500 mg")
        assertThat(result.manufacturers).contains("GSK")
    }

    @Test
    fun ingredientOnItsOwnLine_brandFromLexicon_companyWithoutKeyword() {
        val result = parse(
            "Some promo text" to 30,
            "Augmentin" to 70,
            "amoxicillin / clavulanic acid" to 22,
            "Beecham Group plc, Brentford" to 12,
        )
        assertThat(result.names.first()).isEqualTo("Augmentin")
        // Printed as one phrase, recognised as the co-amoxiclav combination.
        assertThat(result.genericNames.first()).isEqualTo("Amoxicillin / clavulanic acid")
        assertThat(result.manufacturers.first()).isEqualTo("Beecham Group plc")
    }

    @Test
    fun usNamesAreRecognisedToo() {
        val result = parse("TYLENOL" to 80, "Acetaminophen 500 mg" to 25, "Extra Strength" to 20)
        assertThat(result.names.first()).isEqualTo("Tylenol")
        assertThat(result.genericNames.first()).isEqualTo("Acetaminophen")
    }

    @Test
    fun pharmacyDispensingLabel() {
        val result = parse(
            "Al Manara Pharmacy" to 20,
            "Patient: SARA AHMED" to 18,
            "Dr. Hana Saleh" to 16,
            "Date: 12/01/2026" to 16,
            "AMOXICILLIN 250MG/5ML SUSPENSION" to 18,
            "Take 5 ml three times a day for 7 days" to 16,
            "Discard 14 days after opening" to 14,
            "For the treatment of ear infection." to 14,
        )
        assertThat(result.patientNames.first()).isEqualTo("Sara Ahmed")
        assertThat(result.prescribers.first()).isEqualTo("Dr. Hana Saleh")
        assertThat(result.prescriptionDates.first()).isEqualTo(LocalDate.of(2026, 1, 12))
        assertThat(result.useWithinDays).containsExactly(14)
        assertThat(result.conditions.first()).isEqualTo("Ear infection")
        assertThat(result.genericNames.first()).isEqualTo("Amoxicillin")
        assertThat(result.dosageInstructions.first()).startsWith("Take 5 ml three times a day")
    }

    @Test
    fun useWithinVariants_andIndication() {
        assertThat(parse("Use within 28 days of opening" to 14).useWithinDays).containsExactly(28)
        assertThat(parse("Once opened, use within 4 weeks" to 14).useWithinDays).containsExactly(28)
        assertThat(parse("Shelf life after first opening: 6 months" to 14).useWithinDays).containsExactly(180)
        assertThat(parse("For the relief of headache, fever and period pain." to 14).conditions)
            .containsExactly("Headache, fever and period pain")
    }

    @Test
    fun rawTextIsKeptForTheUserToCheck() {
        val result = parse("Panadol" to 60, "Paracetamol 500 mg" to 20)
        assertThat(result.rawText).isEqualTo("Panadol\nParacetamol 500 mg")
    }
}
