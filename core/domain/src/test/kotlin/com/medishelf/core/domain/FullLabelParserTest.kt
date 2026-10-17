package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.scan.LabelTextParser
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.PackQuantity
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.model.RecognizedLine
import org.junit.Test
import java.time.LocalDate

/** Text as ML Kit returns it from photos of several sides of real-world style packs. */
class FullLabelParserTest {
    private val today = LocalDate.of(2026, 1, 15)
    private fun parse(vararg lines: Pair<String, Int>) =
        LabelTextParser.parse(lines.map { RecognizedLine(it.first, it.second) }, today)

    @Test
    fun otcTabletPack_allSides() {
        val result = parse(
            // front
            "PANADOL®" to 90,
            "Advance" to 45,
            "Paracetamol 500 mg" to 40,
            "24 Film-coated Tablets" to 30,
            // back
            "Each film-coated tablet contains:" to 18,
            "Paracetamol 500 mg" to 18,
            "Dosage: Adults and children over 12 years: Take 2 tablets" to 16,
            "every 4-6 hours. Max 8 tablets in 24 hours." to 16,
            "Store below 25°C. Keep out of reach of children." to 16,
            "Manufactured by: GlaxoSmithKline Dungarvan Ltd, Co. Waterford, Ireland" to 14,
            // flap
            "BATCH: 2B7781X" to 14,
            "MFG: 03/2025" to 14,
            "EXP: 02/2028" to 14,
        )
        assertThat(result.names.first()).isEqualTo("Panadol")
        assertThat(result.genericNames.first()).isEqualTo("Paracetamol")
        assertThat(result.strengths.first()).isEqualTo("500 mg")
        assertThat(result.forms.first()).isEqualTo(MedicineForm.TABLET)
        assertThat(result.quantities.first()).isEqualTo(PackQuantity(24.0, QuantityUnit.TABLETS))
        assertThat(result.manufacturers.first()).isEqualTo("GlaxoSmithKline Dungarvan Ltd")
        assertThat(result.expiryDates.first()).isEqualTo(LocalDate.of(2028, 2, 29))
        assertThat(result.batchNumbers.first()).isEqualTo("2B7781X")
        assertThat(result.isPrescription).isFalse()
        assertThat(result.dosageInstructions.first())
            .isEqualTo("Adults and children over 12 years: Take 2 tablets every 4-6 hours. Max 8 tablets in 24 hours.")
        assertThat(result.storageNotes).containsExactly("Store below 25°C.")
    }

    @Test
    fun prescriptionSuspension_combinedIngredientsAndFridge() {
        val result = parse(
            "Augmentin 457mg/5ml" to 80,
            "Powder for oral suspension" to 30,
            "Amoxicillin (as trihydrate) 400 mg" to 20,
            "Clavulanic acid (as potassium clavulanate) 57 mg" to 20,
            "per 5 ml" to 20,
            "POM" to 25,
            "70 ml when reconstituted" to 22,
            "Store in a refrigerator (2°C – 8°C)." to 16,
            "Marketing Authorisation Holder: Beecham Group plc, Brentford, UK" to 14,
            "Lot 4471A  Exp 11/2026" to 14,
        )
        assertThat(result.names.first()).isEqualTo("Augmentin")
        assertThat(result.genericNames.first()).isEqualTo("Amoxicillin + Clavulanic acid")
        assertThat(result.strengths.first()).isEqualTo("457 mg/5 ml")
        assertThat(result.forms.first()).isEqualTo(MedicineForm.SYRUP)
        assertThat(result.quantities.first()).isEqualTo(PackQuantity(70.0, QuantityUnit.ML))
        assertThat(result.isPrescription).isTrue()
        assertThat(result.manufacturers.first()).isEqualTo("Beecham Group plc")
        assertThat(result.storageNotes.first()).isEqualTo("Store in a refrigerator (2°C – 8°C).")
        assertThat(result.batchNumbers.first()).isEqualTo("4471A")
        assertThat(result.expiryDates.first()).isEqualTo(LocalDate.of(2026, 11, 30))
    }

    @Test
    fun insulinPen_blisterMultipackAndCream() {
        val pen = parse(
            "NovoRapid® FlexPen®" to 70,
            "insulin aspart 100 units/ml" to 25,
            "Solution for injection in a pre-filled pen" to 20,
            "5 x 3 ml pre-filled pens" to 20,
            "Rx only" to 18,
            "Novo Nordisk A/S" to 14,
        )
        assertThat(pen.names.first()).isEqualTo("NovoRapid FlexPen")
        assertThat(pen.forms.first()).isEqualTo(MedicineForm.INJECTION)
        assertThat(pen.isPrescription).isTrue()

        val blister = parse("Brufen 400" to 60, "Ibuprofen 400 mg" to 30, "3 x 10 film-coated tablets" to 20)
        assertThat(blister.quantities.first()).isEqualTo(PackQuantity(30.0, QuantityUnit.TABLETS))
        assertThat(blister.genericNames.first()).isEqualTo("Ibuprofen")

        val cream = parse("Fucidin®" to 60, "Fusidic acid 2% w/w Cream" to 25, "Net contents 15 g tube" to 18, "LEO Pharma" to 14)
        assertThat(cream.forms.first()).isEqualTo(MedicineForm.CREAM)
        assertThat(cream.quantities.first()).isEqualTo(PackQuantity(15.0, QuantityUnit.GRAMS))
        assertThat(cream.manufacturers.first()).isEqualTo("LEO Pharma")
        assertThat(cream.genericNames.first()).isEqualTo("Fusidic acid")
    }

    @Test
    fun dosageLinesAreNotMistakenForPackSize() {
        val result = parse("Calpol" to 60, "Take 5 ml every 4 hours" to 16, "100 ml bottle" to 16)
        assertThat(result.quantities).containsExactly(PackQuantity(100.0, QuantityUnit.ML))
        assertThat(result.dosageInstructions.first()).isEqualTo("Take 5 ml every 4 hours")
    }

    @Test
    fun allCapsLabel_isMadeReadable() {
        val result = parse(
            "ZYRTEC" to 60,
            "CETIRIZINE DIHYDROCHLORIDE 10 MG" to 20,
            "DIRECTIONS: ADULTS: TAKE ONE TABLET DAILY." to 14,
            "MANUFACTURED BY: UCB PHARMA SA, BRUSSELS" to 12,
        )
        assertThat(result.names.first()).isEqualTo("Zyrtec")
        assertThat(result.genericNames.first()).isEqualTo("Cetirizine Dihydrochloride")
        assertThat(result.dosageInstructions.first()).isEqualTo("Adults: take one tablet daily.")
        assertThat(result.manufacturers.first()).isEqualTo("Ucb Pharma Sa")
    }
}
