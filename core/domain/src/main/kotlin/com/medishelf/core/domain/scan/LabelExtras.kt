package com.medishelf.core.domain.scan

import java.time.LocalDate
import java.util.Locale

/**
 * Fields beyond the product itself: in-use shelf life, and pharmacy dispensing labels
 * (prescriber, date, patient) plus the indication printed on many packs.
 */
internal object LabelExtras {
    private val useWithin = listOf(
        Regex("""\b(?:USE|DISCARD|CONSUME)\b[^.]{0,30}?\b(?:WITHIN|AFTER)\s+(\d{1,3})\s*(DAYS?|WEEKS?|MONTHS?)\b[^.]{0,40}?\bOPEN"""),
        Regex("""\b(?:ONCE|AFTER|WHEN)\s+(?:FIRST\s+)?OPEN(?:ED|ING)\b[^.]{0,40}?\b(\d{1,3})\s*(DAYS?|WEEKS?|MONTHS?)\b"""),
        Regex("""\b(?:USE|DISCARD|CONSUME)\b\s+(?:WITHIN\s+)?(\d{1,3})\s*(DAYS?|WEEKS?|MONTHS?)\s+(?:AFTER|OF|FROM|FOLLOWING)\s+(?:FIRST\s+)?OPEN"""),
        Regex("""\bSHELF[- ]LIFE AFTER (?:FIRST )?OPENING\b\s*:?\s*(\d{1,3})\s*(DAYS?|WEEKS?|MONTHS?)\b"""),
    )

    fun useWithinDays(lines: List<String>): List<Int> =
        lines.flatMap { line ->
            useWithin.mapNotNull { regex ->
                regex.find(line)?.let { m ->
                    val amount = m.groupValues[1].toInt()
                    amount * when (m.groupValues[2].first()) {
                        'W' -> 7
                        'M' -> 30
                        else -> 1
                    }
                }
            }
        }.filter { it in 1..730 }.distinct()

    private val prescriber = Regex("""\b(?:PRESCRIBER|PRESCRIBED BY|DOCTOR|PHYSICIAN|DR)\b\.?\s*[:.]?\s*((?:DR\.?\s+)?[A-Z][A-Z'\-]+(?:\s+[A-Z][A-Z'\-]+){0,3})""")
    private val prescriberStop = Regex("""\b(EXP|DATE|LOT|BATCH|QTY|QUANTITY|REFILLS?|RX|TAKE)\b.*""")

    /** "Dr. Hana Saleh" from "Dr. HANA SALEH" or "Prescriber: Hana Saleh". */
    fun prescribers(lines: List<Line>): List<String> =
        lines.mapNotNull { line ->
            val m = prescriber.find(line.upper) ?: return@mapNotNull null
            val range = m.groups[1]!!.range
            val name = prescriberStop.replace(line.original.substring(range.first, range.last + 1).uppercase(Locale.ROOT), "")
                .replace(Regex("""^DR\.?\s+"""), "").trim()
            name.takeIf { it.count(Char::isLetter) >= 3 && it.split(' ').size <= 4 }?.let { "Dr. " + it.toDisplayCase() }
        }.distinct().take(LabelDetails.MAX)

    private val dispensedKeyword = Regex("""\b(DISPENSED|DISPENSING DATE|DATE DISPENSED|RX DATE|PRESCRIPTION DATE|DATE OF ISSUE|ISSUED|FILLED|DATE)\b""")

    /** Dispensing/prescription dates: on a keyword line, never in the future, not an expiry. */
    fun prescriptionDates(lines: List<String>, today: LocalDate): List<LocalDate> =
        lines.filter { dispensedKeyword.containsMatchIn(it) && !LabelDates.expiryKeywords.containsMatchIn(it) && !LabelDates.manufactureKeywords.containsMatchIn(it) }
            .flatMap { LabelDates.datesIn(it, today) }
            .filter { !it.isAfter(today) && it.isAfter(today.minusYears(3)) }
            .distinct()
            .take(LabelDetails.MAX)

    private val patient = Regex("""\b(?:PATIENT(?:'S)?(?:\s+NAME)?|NAME|PT|FOR)\s*:\s*([A-Z][A-Z'\-]+(?:\s+[A-Z][A-Z'\-]+){0,3})""")

    fun patientNames(lines: List<Line>): List<String> =
        lines.mapNotNull { line ->
            val m = patient.find(line.upper) ?: return@mapNotNull null
            val range = m.groups[1]!!.range
            line.original.substring(range.first, range.last + 1).trim().toDisplayCase()
                .takeIf { it.count(Char::isLetter) >= 2 }
        }.distinct().take(LabelDetails.MAX)

    private val indication = Regex(
        """\b(?:FOR (?:THE )?(?:SHORT[- ]TERM |SYMPTOMATIC |TEMPORARY )?(?:RELIEF|TREATMENT|PREVENTION|MANAGEMENT|CONTROL) OF|INDICATED FOR(?: THE TREATMENT OF)?|INDICATIONS?\s*:|USED (?:TO TREAT|FOR))\s*(?:THE\s+)?(.+)""",
    )

    /** "Headache, fever and period pain" from "For the relief of headache, fever and period pain." */
    fun conditions(lines: List<Line>): List<String> =
        lines.mapNotNull { line ->
            val m = indication.find(line.upper) ?: return@mapNotNull null
            val range = m.groups[1]!!.range
            line.original.substring(range.first).substringBefore('.').trim(' ', ',', ':', ';')
                .takeIf { it.length in 3..90 }
                ?.let { it.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }
        }.distinct().take(LabelDetails.MAX)
}
