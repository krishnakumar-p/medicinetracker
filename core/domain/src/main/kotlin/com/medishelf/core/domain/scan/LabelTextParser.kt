package com.medishelf.core.domain.scan

import com.medishelf.core.model.LabelSuggestions
import com.medishelf.core.model.RecognizedLine
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Turns OCR lines from a medicine box into ranked suggestions. Heuristic by nature, so the UI
 * always asks the user to pick and confirm; nothing here is trusted blindly.
 */
object LabelTextParser {
    private val expiryKeywords = Regex("""\b(EXP|EXPIRY|EXPIRES|EXP\.? ?DATE|USE BY|USE BEFORE|BEST BEFORE|BB|ED|VAL|VALID|VERW\.? ?BIS|EXD)\b""")
    private val manufactureKeywords = Regex("""\b(MFG|MFD|MFGD|MANUF|MANUFACTURED|MANUFACTURING|PROD|PRODUCTION|MF|M\.D|DOM|PD)\b""")
    private val batchRegex = Regex("""\b(?:LOT|BATCH|B\. ?NO|B/NO|BN|BNO|CH\.?-?B|CHARGE)\b\.?\s*(?:NO\.?|NUMBER)?\s*[:.#]?\s*([A-Z0-9][A-Z0-9-/]{2,19})""")
    private val strengthRegex = Regex("""\b(\d+(?:[.,]\d+)?)\s?(MG|MCG|µG|UG|G|ML|IU|%)(?:\s?/\s?(\d+(?:[.,]\d+)?)?\s?(ML|G|DOSE|TAB))?\b""")
    private val nonNameWords = Regex("""\b(EXP|LOT|BATCH|MFG|MFD|TABLETS?|CAPSULES?|FILM|COATED|ORAL|USE|STORE|KEEP|READ|LEAFLET|PRESCRIPTION|ONLY|EACH|CONTAINS|BELOW|REACH|CHILDREN|DOSAGE|PRICE|SR|RX)\b""")

    private val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "SEPT" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
    )

    private data class Scored<T>(val value: T, val score: Int)

    fun parse(lines: List<RecognizedLine>, today: LocalDate): LabelSuggestions {
        val upper = lines.map { it.copy(text = it.text.uppercase(Locale.ROOT).trim()) }
        return LabelSuggestions(
            names = names(upper),
            strengths = strengths(upper),
            expiryDates = expiries(upper, today),
            batchNumbers = batches(upper),
        )
    }

    // --- Expiry -----------------------------------------------------------------------------

    private fun expiries(lines: List<RecognizedLine>, today: LocalDate): List<LocalDate> {
        val candidates = mutableListOf<Scored<LocalDate>>()
        lines.forEachIndexed { index, line ->
            val previous = lines.getOrNull(index - 1)?.text.orEmpty()
            var bonus = 0
            if (expiryKeywords.containsMatchIn(line.text)) bonus += 5
            else if (expiryKeywords.containsMatchIn(previous) && !manufactureKeywords.containsMatchIn(previous)) bonus += 3
            if (manufactureKeywords.containsMatchIn(line.text) && !expiryKeywords.containsMatchIn(line.text)) bonus -= 8
            // When a line holds both MFG and EXP dates, the later one is the expiry.
            val dates = datesIn(line.text, today)
            dates.forEachIndexed { i, date ->
                var score = bonus
                if (date.isAfter(today)) score += 2
                if (dates.size > 1 && date == dates.maxOrNull()) score += 2
                if (i > 0 && dates.size > 1) score += 1
                candidates += Scored(date, score)
            }
        }
        return candidates
            .filter { it.value.isAfter(today.minusYears(5)) && it.value.isBefore(today.plusYears(15)) }
            .sortedByDescending { it.score }
            .map { it.value }
            .distinct()
            .take(MAX_CANDIDATES)
    }

    private val dmy = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{4}|\d{2})\b""")
    private val ymd = Regex("""\b(\d{4})[./-](\d{1,2})(?:[./-](\d{1,2}))?\b""")
    private val my = Regex("""\b(\d{1,2})\s?[./-]\s?(\d{4}|\d{2})\b""")
    private val dMonY = Regex("""\b(?:(\d{1,2})[ .-]?)?(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEPT?|OCT|NOV|DEC)[A-Z]*[ .,/-]?\s?(\d{4}|\d{2})\b""")

    /** Dates in reading order. Month-only dates resolve to the month's last day (pack convention). */
    internal fun datesIn(text: String, today: LocalDate): List<LocalDate> {
        val found = sortedMapOf<Int, LocalDate>()
        val taken = mutableListOf<IntRange>()
        fun claim(range: IntRange, date: LocalDate?) {
            if (date == null || taken.any { it.first <= range.last && range.first <= it.last }) return
            taken += range
            found[range.first] = date
        }
        dMonY.findAll(text).forEach { m ->
            val month = months[m.groupValues[2].take(4).let { if (it == "SEPT") it else it.take(3) }] ?: return@forEach
            val year = fullYear(m.groupValues[3], today)
            val day = m.groupValues[1].toIntOrNull()
            claim(m.range, safeDate(year, month, day))
        }
        ymd.findAll(text).forEach { m ->
            claim(m.range, safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull()))
        }
        dmy.findAll(text).forEach { m ->
            claim(m.range, safeDate(fullYear(m.groupValues[3], today), m.groupValues[2].toInt(), m.groupValues[1].toInt()))
        }
        my.findAll(text).forEach { m ->
            claim(m.range, safeDate(fullYear(m.groupValues[2], today), m.groupValues[1].toInt(), null))
        }
        return found.values.toList()
    }

    private fun fullYear(value: String, today: LocalDate): Int =
        if (value.length == 4) value.toInt() else (today.year / 100) * 100 + value.toInt()

    private fun safeDate(year: Int, month: Int, day: Int?): LocalDate? {
        if (month !in 1..12 || year !in 1990..2100) return null
        val ym = YearMonth.of(year, month)
        return if (day == null) ym.atEndOfMonth() else runCatching { ym.atDay(day) }.getOrNull()
    }

    // --- Batch, strength, name --------------------------------------------------------------

    private fun batches(lines: List<RecognizedLine>): List<String> =
        lines.flatMap { line -> batchRegex.findAll(line.text).map { it.groupValues[1].trimEnd('-', '/') } }
            .filter { candidate -> candidate.any(Char::isDigit) }
            .distinct()
            .take(MAX_CANDIDATES)

    private fun strengths(lines: List<RecognizedLine>): List<String> =
        lines.flatMap { line -> strengthRegex.findAll(line.text).map { formatStrength(it) } }
            .distinct()
            .take(MAX_CANDIDATES)

    private fun formatStrength(match: MatchResult): String {
        val (amount, unit, perAmount, perUnit) = match.destructured
        val base = "${amount.replace(',', '.')} ${unit.lowercase(Locale.ROOT).replace("ug", "mcg").replace("iu", "IU")}"
        return if (perUnit.isEmpty()) base else "$base/${perAmount.replace(',', '.')}${if (perAmount.isEmpty()) "" else " "}${perUnit.lowercase(Locale.ROOT)}"
    }

    private fun names(lines: List<RecognizedLine>): List<String> =
        lines.asSequence()
            .map { it.copy(text = it.text.replace(Regex("""[®™©]"""), "").trim()) }
            .filter { line ->
                val letters = line.text.count(Char::isLetter)
                letters >= 3 &&
                    line.text.length <= 40 &&
                    letters >= line.text.length * 0.6 &&
                    !nonNameWords.containsMatchIn(line.text) &&
                    !expiryKeywords.containsMatchIn(line.text) &&
                    !manufactureKeywords.containsMatchIn(line.text)
            }
            .sortedByDescending { it.height }
            .map { it.text.toTitleCase() }
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()

    private fun String.toTitleCase(): String =
        lowercase(Locale.ROOT).split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    private const val MAX_CANDIDATES = 3
}
