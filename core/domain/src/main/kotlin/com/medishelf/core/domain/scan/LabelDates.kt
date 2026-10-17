package com.medishelf.core.domain.scan

import java.time.LocalDate
import java.time.YearMonth

/** Expiry-date extraction from upper-cased OCR lines. */
internal object LabelDates {
    val expiryKeywords = Regex("""\b(EXP|EXPIRY|EXPIRES|EXP\.? ?DATE|USE BY|USE BEFORE|BEST BEFORE|BB|ED|VAL|VALID|VERW\.? ?BIS|EXD)\b""")
    val manufactureKeywords = Regex("""\b(MFG|MFD|MFGD|MANUF|MANUFACTURED|MANUFACTURING|PROD|PRODUCTION|MF|M\.D|DOM|PD)\b""")

    private val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "SEPT" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
    )

    private val dmy = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{4}|\d{2})\b""")
    private val ymd = Regex("""\b(\d{4})[./-](\d{1,2})(?:[./-](\d{1,2}))?\b""")
    private val my = Regex("""\b(\d{1,2})\s?[./-]\s?(\d{4}|\d{2})\b""")
    private val dMonY = Regex("""\b(?:(\d{1,2})[ .-]?)?(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEPT?|OCT|NOV|DEC)[A-Z]*[ .,/-]?\s?(\d{4}|\d{2})\b""")

    private data class Scored(val date: LocalDate, val score: Int)

    /** Expiry candidates, best first: keyword lines win, manufacture dates sink. */
    fun expiries(lines: List<String>, today: LocalDate, max: Int): List<LocalDate> {
        val candidates = mutableListOf<Scored>()
        lines.forEachIndexed { index, line ->
            val previous = lines.getOrNull(index - 1).orEmpty()
            var bonus = 0
            if (expiryKeywords.containsMatchIn(line)) bonus += 5
            else if (expiryKeywords.containsMatchIn(previous) && !manufactureKeywords.containsMatchIn(previous)) bonus += 3
            if (manufactureKeywords.containsMatchIn(line) && !expiryKeywords.containsMatchIn(line)) bonus -= 8
            // When a line holds both MFG and EXP dates, the later one is the expiry.
            val dates = datesIn(line, today)
            dates.forEachIndexed { i, date ->
                var score = bonus
                if (date.isAfter(today)) score += 2
                if (dates.size > 1 && date == dates.maxOrNull()) score += 2
                if (i > 0 && dates.size > 1) score += 1
                candidates += Scored(date, score)
            }
        }
        return candidates
            .filter { it.date.isAfter(today.minusYears(5)) && it.date.isBefore(today.plusYears(15)) }
            .sortedByDescending { it.score }
            .map { it.date }
            .distinct()
            .take(max)
    }

    /** Dates in reading order. Month-only dates resolve to the month's last day (pack convention). */
    fun datesIn(text: String, today: LocalDate): List<LocalDate> {
        val found = sortedMapOf<Int, LocalDate>()
        val taken = mutableListOf<IntRange>()
        fun claim(range: IntRange, date: LocalDate?) {
            if (date == null || taken.any { it.first <= range.last && range.first <= it.last }) return
            taken += range
            found[range.first] = date
        }
        dMonY.findAll(text).forEach { m ->
            val month = months[m.groupValues[2].take(4).let { if (it == "SEPT") it else it.take(3) }] ?: return@forEach
            claim(m.range, safeDate(fullYear(m.groupValues[3], today), month, m.groupValues[1].toIntOrNull()))
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
}
