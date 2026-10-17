package com.medishelf.core.nlp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MedicalEntityRecognizerTest {
    private val ner = MedicalEntityRecognizer()

    private fun entities(vararg lines: String) = ner.recognize(lines.toList()).map { it.type to it.value }

    @Test
    fun lexiconLoaded() {
        val lexicon = MedicineLexicon.default
        assertThat(lexicon.size(EntityType.INGREDIENT)).isGreaterThan(3000)
        assertThat(lexicon.size(EntityType.BRAND)).isGreaterThan(4000)
        assertThat(lexicon.size(EntityType.MANUFACTURER)).isGreaterThan(2000)
    }

    @Test
    fun findsIngredientsAnywhereInText_withInnAndUsNames() {
        assertThat(entities("Each tablet contains Paracetamol BP 500mg"))
            .contains(EntityType.INGREDIENT to "Paracetamol")
        assertThat(entities("Acetaminophen 325 mg")).containsExactly(EntityType.INGREDIENT to "Acetaminophen")
        assertThat(entities("Salbutamol 100 micrograms per actuation")).contains(EntityType.INGREDIENT to "Salbutamol")
    }

    @Test
    fun longestSpanWins_forMultiWordIngredients() {
        assertThat(entities("Clavulanic acid 57 mg")).containsExactly(EntityType.INGREDIENT to "Clavulanic acid") // as printed
        assertThat(entities("CETIRIZINE HYDROCHLORIDE 10 MG")).containsExactly(EntityType.INGREDIENT to "Cetirizine Hydrochloride")
    }

    @Test
    fun toleratesOcrErrors_andReturnsCorrectSpelling() {
        assertThat(entities("PARACETAM0L 500 MG")).contains(EntityType.INGREDIENT to "Paracetamol") // zero for O
        assertThat(entities("Amoxicilin 250mg")).contains(EntityType.INGREDIENT to "Amoxicillin") // missing letter
        assertThat(entities("Ibuprofem 400 mg")).contains(EntityType.INGREDIENT to "Ibuprofen") // n read as m
    }

    @Test
    fun recognisesBrandsAndManufacturers() {
        assertThat(entities("Augmentin")).containsExactly(EntityType.BRAND to "Augmentin")
        assertThat(entities("PANADOL Extra")).contains(EntityType.BRAND to "Panadol Extra")
        assertThat(entities("Marketed by Julphar, Ras Al Khaimah")).contains(EntityType.MANUFACTURER to "Julphar")
        assertThat(entities("Dr. Reddy's Laboratories Ltd")).contains(EntityType.MANUFACTURER to "Dr. Reddy's Laboratories")
        assertThat(entities("Novo Nordisk A/S")).contains(EntityType.MANUFACTURER to "Novo Nordisk")
    }

    @Test
    fun ignoresEverydayWords() {
        assertThat(entities("Keep out of the reach and sight of children")).isEmpty()
        assertThat(entities("Store below 25 C in a dry place")).isEmpty()
        assertThat(entities("Read the leaflet before use")).isEmpty()
    }
}
