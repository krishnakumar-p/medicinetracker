package com.medishelf.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/** PBKDF2-HMAC-SHA256 with a per-install random salt; comparison is constant-time. */
class PinHasher @Inject constructor() {
    data class Hashed(val hash: String, val salt: String, val iterations: Int)

    fun hash(pin: String, iterations: Int = ITERATIONS): Hashed {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return Hashed(encode(derive(pin, salt, iterations)), encode(salt), iterations)
    }

    fun matches(pin: String, stored: Hashed): Boolean {
        val actual = derive(pin, decode(stored.salt), stored.iterations)
        return MessageDigest.isEqual(actual, decode(stored.hash))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)

    companion object {
        const val ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}
