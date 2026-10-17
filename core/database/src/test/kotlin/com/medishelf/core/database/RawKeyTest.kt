package com.medishelf.core.database

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.database.di.toSqlCipherRawKey
import org.junit.Test

class RawKeyTest {
    @Test
    fun formatsAs64HexCharRawKeyLiteral() {
        val key = ByteArray(32) { it.toByte() }.toSqlCipherRawKey().toString(Charsets.US_ASCII)
        assertThat(key).isEqualTo("x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'")
        assertThat(ByteArray(32) { -1 }.toSqlCipherRawKey().toString(Charsets.US_ASCII)).contains("ffff")
    }
}
