package com.medishelf.core.domain.scan

import com.medishelf.core.model.ScanSuggestion
import java.time.LocalDate
import java.time.YearMonth

/**
 * Parses GS1 element strings as printed in the 2D DataMatrix on most medicine packs
 * (EU FMD, Saudi/UAE track-and-trace, US DSCSA), e.g. "]d2 01 GTIN 17 YYMMDD 10 BATCH <GS> 21 SERIAL".
 * Accepts both the raw FNC1/GS-separated form and the human-readable "(01)...(17)..." form.
 */
object Gs1Parser {
    private const val GS = '\u001D'

    /** Fixed-length AIs (data length) we may meet before or between the ones we need. */
    private val fixedLength = mapOf(
        "00" to 18, "01" to 14, "02" to 14, "11" to 6, "12" to 6, "13" to 6,
        "15" to 6, "16" to 6, "17" to 6, "20" to 2,
    )
    private val variableAis = setOf("10", "21", "22", "30", "37", "90", "91", "92", "93", "94", "95", "96", "97", "98", "99")
    private val variableAis3 = setOf("240", "241", "250", "251", "710", "711", "712", "713", "714")

    data class Gs1Data(
        val gtin: String? = null,
        val expiry: LocalDate? = null,
        val batch: String? = null,
        val serial: String? = null,
    )

    fun parse(raw: String, today: LocalDate): Gs1Data? {
        val elements = elements(raw) ?: return null
        val gtin = elements["01"]?.takeIf { it.length == 14 && it.all(Char::isDigit) } ?: return null
        return Gs1Data(
            gtin = gtin,
            expiry = elements["17"]?.let { parseYymmdd(it, today) },
            batch = elements["10"]?.takeIf(String::isNotBlank),
            serial = elements["21"]?.takeIf(String::isNotBlank),
        )
    }

    /** GS1 data as a scan suggestion; the GTIN is normalised so it matches the box EAN-13. */
    fun toSuggestion(data: Gs1Data): ScanSuggestion = ScanSuggestion(
        barcode = data.gtin?.let(BarcodeNormalizer::normalize),
        expiryDate = data.expiry,
        batchNumber = data.batch,
    )

    private fun elements(raw: String): Map<String, String>? {
        val text = raw.trim()
            .removePrefix("]d2").removePrefix("]C1").removePrefix("]Q3").removePrefix("]e0")
            .trimStart(GS)
        if (text.startsWith("(")) return bracketed(text)
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < text.length) {
            if (text[i] == GS) { i++; continue }
            val ai2 = text.substring(i, minOf(i + 2, text.length))
            val ai3 = text.substring(i, minOf(i + 3, text.length))
            when {
                ai2 in fixedLength -> {
                    val end = i + 2 + fixedLength.getValue(ai2)
                    if (end > text.length) return null
                    result[ai2] = text.substring(i + 2, end)
                    i = end
                }
                ai3 in variableAis3 || ai2 in variableAis -> {
                    val aiLen = if (ai3 in variableAis3) 3 else 2
                    val start = i + aiLen
                    val end = text.indexOf(GS, start).let { if (it == -1) text.length else it }
                    result[text.substring(i, start)] = text.substring(start, end)
                    i = end
                }
                else -> break // Unknown AI: keep what we have rather than mis-parse the rest.
            }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun bracketed(text: String): Map<String, String>? {
        val matches = Regex("""\((\d{2,4})\)([^(]*)""").findAll(text).toList()
        return matches.associate { it.groupValues[1] to it.groupValues[2].trim() }.takeIf { it.isNotEmpty() }
    }

    /** GS1 dates: YYMMDD with DD=00 meaning end of month; century per the GS1 sliding window. */
    internal fun parseYymmdd(value: String, today: LocalDate): LocalDate? {
        if (value.length != 6 || !value.all(Char::isDigit)) return null
        val yy = value.substring(0, 2).toInt()
        val mm = value.substring(2, 4).toInt()
        val dd = value.substring(4, 6).toInt()
        if (mm !in 1..12) return null
        val currentYy = today.year % 100
        val century = today.year - currentYy
        val year = when (val diff = yy - currentYy) {
            in 51..99 -> century - 100 + yy
            in -99..-50 -> century + 100 + yy
            else -> century + yy
        }
        val month = YearMonth.of(year, mm)
        return if (dd == 0) month.atEndOfMonth() else runCatching { month.atDay(dd) }.getOrNull()
    }
}

object BarcodeNormalizer {
    /**
     * Strips the GTIN-14 packaging indicator "0" so a DataMatrix GTIN and the EAN-13 on the
     * same box compare equal. Non-numeric codes are returned trimmed.
     */
    fun normalize(code: String): String {
        val trimmed = code.trim()
        return if (trimmed.length == 14 && trimmed.startsWith("0") && trimmed.all(Char::isDigit)) trimmed.substring(1) else trimmed
    }
}
