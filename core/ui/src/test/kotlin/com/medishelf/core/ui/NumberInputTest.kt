package com.medishelf.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NumberInputTest {
    @Test
    fun parsesDotOrCommaDecimals() {
        assertThat(parseLocalizedNumber("12.5")).isEqualTo(12.5)
        assertThat(parseLocalizedNumber(" 7,25 ")).isEqualTo(7.25)
        assertThat(parseLocalizedNumber("0")).isEqualTo(0.0)
    }

    @Test
    fun rejectsInvalid() {
        assertThat(parseLocalizedNumber("")).isNull()
        assertThat(parseLocalizedNumber("1.2.3")).isNull()
        assertThat(parseLocalizedNumber("abc")).isNull()
        assertThat(parseLocalizedNumber("NaN")).isNull()
        assertThat(parseLocalizedNumber("-1")).isNull() // sign handled by validation, not typed
        assertThat(parseLocalizedNumber("1e5")).isNull()
        assertThat(parseLocalizedInt("2.5")).isNull()
        assertThat(parseLocalizedInt("28")).isEqualTo(28)
    }

    @Test
    fun editableString_dropsTrailingZero() {
        assertThat(10.0.toEditableString()).isEqualTo("10")
        assertThat(2.5.toEditableString()).isEqualTo("2.5")
    }
}
