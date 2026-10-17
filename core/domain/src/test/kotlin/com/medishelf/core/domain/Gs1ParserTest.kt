package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.scan.BarcodeNormalizer
import com.medishelf.core.domain.scan.Gs1Parser
import org.junit.Test
import java.time.LocalDate

class Gs1ParserTest {
    private val today = LocalDate.of(2026, 1, 15)
    private val gs = '\u001D'

    @Test
    fun rawDataMatrix_withFnc1Separators() {
        val raw = "]d201062912345678901727033110AB12C${gs}21SN998877"
        val data = Gs1Parser.parse(raw, today)!!
        assertThat(data.gtin).isEqualTo("06291234567890")
        assertThat(data.expiry).isEqualTo(LocalDate.of(2027, 3, 31))
        assertThat(data.batch).isEqualTo("AB12C")
        assertThat(data.serial).isEqualTo("SN998877")
    }

    @Test
    fun serialBeforeBatch_andDayZeroMeansEndOfMonth() {
        val raw = "0106291234567890" + "21XYZ$gs" + "17280200" + "10LOT-7"
        val data = Gs1Parser.parse(raw, today)!!
        assertThat(data.expiry).isEqualTo(LocalDate.of(2028, 2, 29))
        assertThat(data.batch).isEqualTo("LOT-7")
        assertThat(data.serial).isEqualTo("XYZ")
    }

    @Test
    fun humanReadableBracketForm() {
        val data = Gs1Parser.parse("(01)05012345678900(17)261130(10)B4512", today)!!
        assertThat(data.expiry).isEqualTo(LocalDate.of(2026, 11, 30))
        assertThat(data.batch).isEqualTo("B4512")
    }

    @Test
    fun suggestion_normalisesGtinToEan13() {
        val data = Gs1Parser.parse("0106291234567890", today)!!
        assertThat(Gs1Parser.toSuggestion(data).barcode).isEqualTo("6291234567890")
        assertThat(BarcodeNormalizer.normalize(" 6291234567890 ")).isEqualTo("6291234567890")
        assertThat(BarcodeNormalizer.normalize("14291234567890")).isEqualTo("14291234567890")
    }

    @Test
    fun nonGs1Codes_returnNull() {
        assertThat(Gs1Parser.parse("6291234567890", today)).isNull()
        assertThat(Gs1Parser.parse("hello", today)).isNull()
        assertThat(Gs1Parser.parse("01123", today)).isNull()
    }

    @Test
    fun centuryWindow() {
        assertThat(Gs1Parser.parseYymmdd("991231", today)).isEqualTo(LocalDate.of(1999, 12, 31))
        assertThat(Gs1Parser.parseYymmdd("450101", today)).isEqualTo(LocalDate.of(2045, 1, 1))
        assertThat(Gs1Parser.parseYymmdd("261301", today)).isNull()
    }
}
