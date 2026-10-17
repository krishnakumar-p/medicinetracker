package com.medishelf.core.domain.scan

import com.medishelf.core.model.LabelSuggestions
import com.medishelf.core.model.RecognizedLine
import java.time.LocalDate
import java.util.Locale

/**
 * Turns OCR lines from every photographed side of a medicine pack into ranked suggestions for
 * each editor field. Heuristic by nature, so the UI always asks the user to pick and confirm.
 */
object LabelTextParser {
    private val batchRegex = Regex("""\b(?:LOT|BATCH|B\. ?NO|B/NO|BN|BNO|CH\.?-?B|CHARGE)\b\.?\s*(?:NO\.?|NUMBER)?\s*[:.#]?\s*([A-Z0-9][A-Z0-9-/]{2,19})""")
    private val trademark = Regex("""[®™©]""")
    private val nonNameWords = Regex(
        """\b(EXP|LOT|BATCH|MFG|MFD|TABLETS?|CAPSULES?|CAPLETS?|FILM|COATED|ORAL|USE|STORE|KEEP|READ|LEAFLET|PRESCRIPTION|ONLY|EACH|CONTAINS|BELOW|REACH|CHILDREN|DOSAGE|DIRECTIONS|PRICE|RX|POM|POWDER|SOLUTION|SUSPENSION|SYRUP|DROPS|CREAM|OINTMENT|GEL|INJECTION|INHALER|INGREDIENTS?|COMPOSITION|PER|ML|WHEN|RECONSTITUTED|WARNING|ADULTS?)\b""",
    )

    fun parse(recognized: List<RecognizedLine>, today: LocalDate): LabelSuggestions {
        val lines = recognized
            .map { Line(it.text.trim(), it.text.uppercase(Locale.ROOT).trim(), it.height) }
            .filter { it.upper.isNotEmpty() }
        val upper = lines.map { it.upper }
        val names = names(lines)
        return LabelSuggestions(
            names = names,
            genericNames = LabelDetails.genericNames(lines, names.firstOrNull()),
            strengths = strengths(upper),
            forms = LabelDetails.forms(lines),
            quantities = LabelDetails.quantities(lines),
            manufacturers = LabelDetails.manufacturers(lines),
            expiryDates = LabelDates.expiries(upper, today, LabelDetails.MAX),
            batchNumbers = batches(upper),
            isPrescription = LabelDetails.isPrescription(lines),
            dosageInstructions = LabelDetails.dosageInstructions(lines),
            storageNotes = LabelDetails.storageNotes(lines),
        )
    }

    private fun batches(lines: List<String>): List<String> =
        lines.flatMap { line -> batchRegex.findAll(line).map { it.groupValues[1].trimEnd('-', '/') } }
            .filter { candidate -> candidate.any(Char::isDigit) }
            .distinct()
            .take(LabelDetails.MAX)

    private fun strengths(lines: List<String>): List<String> =
        lines.flatMap { line -> LabelDetails.strengthRegex.findAll(line).map { formatStrength(it) } }
            .distinct()
            .take(LabelDetails.MAX)

    private fun formatStrength(match: MatchResult): String {
        val (amount, unit, perAmount, perUnit) = match.destructured
        val base = "${amount.replace(',', '.')} ${unit.lowercase(Locale.ROOT).replace("ug", "mcg").replace("iu", "IU")}"
        return if (perUnit.isEmpty()) base else "$base/${perAmount.replace(',', '.')}${if (perAmount.isEmpty()) "" else " "}${perUnit.lowercase(Locale.ROOT)}"
    }

    /**
     * Brand-name candidates: short, mostly-letter lines ranked by printed size, with a boost for
     * a trademark symbol. Strengths are stripped ("Augmentin 457mg/5ml" -> "Augmentin").
     */
    private fun names(lines: List<Line>): List<String> =
        lines.asSequence()
            .map { line ->
                val marked = trademark.containsMatchIn(line.original)
                val text = LabelDetails.stripStrengths(trademark.replace(line.original, ""))
                Triple(text, line.height * (if (marked) 3 else 2), text.uppercase(Locale.ROOT))
            }
            .filter { (text, _, upper) ->
                val letters = text.count(Char::isLetter)
                letters >= 3 &&
                    text.length <= 40 &&
                    letters >= text.length * 0.6 &&
                    !nonNameWords.containsMatchIn(upper) &&
                    !LabelDates.expiryKeywords.containsMatchIn(upper) &&
                    !LabelDates.manufactureKeywords.containsMatchIn(upper) &&
                    !LabelDetails.isManufacturerLine(upper) &&
                    !LabelDetails.isPrescriptionLine(upper) &&
                    !LabelDetails.isStorageLine(upper) &&
                    !LabelDetails.isDosageLine(upper) &&
                    !LabelDetails.isIngredientLine(upper)
            }
            .sortedByDescending { it.second }
            .map { it.first.toDisplayCase() }
            .distinct()
            .take(LabelDetails.MAX)
            .toList()
}
