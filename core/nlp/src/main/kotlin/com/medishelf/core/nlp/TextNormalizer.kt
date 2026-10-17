package com.medishelf.core.nlp

import java.text.Normalizer
import java.util.Locale

/** Canonical keys for matching OCR text against lexicon entries. */
object TextNormalizer {
    private val marks = Regex("""\p{Mn}+""")
    private val nonKey = Regex("""[^A-Z0-9 ]+""")
    private val spaces = Regex("""\s+""")

    /**
     * Upper case, accents removed, apostrophes dropped ("Reddy's" -> "REDDYS"), "&" as "AND",
     * hyphens and other punctuation as spaces. Lexicon and OCR text go through the same steps.
     */
    fun key(text: String): String {
        val ascii = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        return ascii.uppercase(Locale.ROOT)
            .replace("'", "").replace("’", "")
            .replace("&", " AND ")
            .replace("ß", "SS")
            .let { nonKey.replace(it, " ") }
            .let { spaces.replace(it, " ") }
            .trim()
    }

    /**
     * Repairs common OCR digit/letter confusions inside words ("PARACETAM0L" -> "PARACETAMOL").
     * Only applied to mostly-letter tokens, so strengths like "500" or codes like "B12" are untouched.
     */
    fun repairToken(token: String): String {
        val letters = token.count(Char::isLetter)
        val digits = token.count(Char::isDigit)
        if (digits == 0 || letters < 3 || digits > 2) return token
        return buildString {
            token.forEach { c ->
                append(
                    when (c) {
                        '0' -> 'O'
                        '1' -> 'I'
                        '5' -> 'S'
                        '8' -> 'B'
                        '6' -> 'G'
                        else -> c
                    },
                )
            }
        }
    }
}
