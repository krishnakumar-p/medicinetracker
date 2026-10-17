package com.medishelf.core.domain.scan

import com.medishelf.core.model.LabelSuggestions
import com.medishelf.core.model.RecognizedLine
import com.medishelf.core.nlp.EntityType
import com.medishelf.core.nlp.MedicalEntity
import com.medishelf.core.nlp.MedicalEntityRecognizer
import java.time.LocalDate
import java.util.Locale

/**
 * Turns OCR lines from every photographed side of a medicine pack into ranked suggestions for
 * each editor field. Combines dictionary-based entity recognition (ingredients, brands,
 * manufacturers; OCR-tolerant) with layout and keyword rules. Heuristic by nature, so the UI
 * always asks the user to pick and confirm.
 */
object LabelTextParser {
    private val defaultRecognizer by lazy { MedicalEntityRecognizer() }
    private val excipientKeyword = Regex("""\b(EXCIPIENTS?|INACTIVE INGREDIENTS?|OTHER INGREDIENTS?|ALSO CONTAINS|NON-?MEDICINAL)\b""")

    private val batchRegex = Regex("""\b(?:LOT|BATCH|B\. ?NO|B/NO|BN|BNO|CH\.?-?B|CHARGE)\b\.?\s*(?:NO\.?|NUMBER)?\s*[:.#]?\s*([A-Z0-9][A-Z0-9-/]{2,19})""")
    private val trademark = Regex("""[®™©]""")
    private val nonNameWords = Regex(
        """\b(EXP|LOT|BATCH|MFG|MFD|TABLETS?|CAPSULES?|CAPLETS?|FILM|COATED|ORAL|USE|STORE|KEEP|READ|LEAFLET|PRESCRIPTION|ONLY|EACH|CONTAINS|BELOW|REACH|CHILDREN|DOSAGE|DIRECTIONS|PRICE|RX|POM|POWDER|SOLUTION|SUSPENSION|SYRUP|DROPS|CREAM|OINTMENT|GEL|INJECTION|INHALER|INGREDIENTS?|COMPOSITION|PER|ML|WHEN|RECONSTITUTED|WARNING|ADULTS?)\b""",
    )

    fun parse(
        recognized: List<RecognizedLine>,
        today: LocalDate,
        recognizer: MedicalEntityRecognizer? = defaultRecognizer,
    ): LabelSuggestions {
        val lines = recognized
            .map { Line(it.text.trim(), it.text.uppercase(Locale.ROOT).trim(), it.height) }
            .filter { it.upper.isNotEmpty() }
        val upper = lines.map { it.upper }
        val entities = recognizer?.recognize(lines.map { it.original }).orEmpty()
        val excipients = excipientLines(upper)
        val ingredients = entities.filter {
            it.type == EntityType.INGREDIENT && it.line !in excipients && !insideBrackets(lines[it.line].original, it.start)
        }
        val names = rankNames(names(lines), entities.filter { it.type == EntityType.BRAND }, lines)
        val brand = names.firstOrNull()
        return LabelSuggestions(
            names = names,
            genericNames = mergeIgnoringCase(
                nerIngredients(ingredients, lines),
                LabelDetails.genericNames(lines.filterIndexed { i, _ -> i !in excipients }, brand),
            ),
            // Strength printed beside the brand ("Augmentin 457mg/5ml") describes the whole product.
            strengths = mergeIgnoringCase(brandLineStrengths(brand, lines), pairedStrengths(ingredients, lines), strengths(upper)),
            forms = LabelDetails.forms(lines),
            quantities = LabelDetails.quantities(lines),
            manufacturers = mergeIgnoringCase(
                LabelDetails.manufacturers(lines).take(1),
                nerManufacturers(entities.filter { it.type == EntityType.MANUFACTURER }, lines),
                LabelDetails.manufacturers(lines),
            ),
            expiryDates = LabelDates.expiries(upper, today, LabelDetails.MAX),
            batchNumbers = batches(upper),
            isPrescription = LabelDetails.isPrescription(lines),
            dosageInstructions = LabelDetails.dosageInstructions(lines),
            storageNotes = LabelDetails.storageNotes(lines),
            useWithinDays = LabelExtras.useWithinDays(upper),
            prescribers = LabelExtras.prescribers(lines),
            prescriptionDates = LabelExtras.prescriptionDates(upper, today),
            patientNames = LabelExtras.patientNames(lines),
            conditions = LabelExtras.conditions(lines),
            rawText = lines.joinToString("\n") { it.original },
        )
    }

    /** Lines in an "Excipients:" / "Also contains:" section, whose substances aren't active ingredients. */
    private fun excipientLines(upper: List<String>): Set<Int> = buildSet {
        upper.forEachIndexed { i, line ->
            if (!excipientKeyword.containsMatchIn(line)) return@forEachIndexed
            add(i)
            // The list often wraps onto following lines, until another section starts.
            var j = i + 1
            while (j < upper.size && j <= i + 3 && !LabelDetails.isIngredientLine(upper[j]) &&
                !LabelDetails.isDosageLine(upper[j]) && !LabelDetails.isStorageLine(upper[j]) &&
                !LabelDates.expiryKeywords.containsMatchIn(upper[j])
            ) add(j++)
        }
    }

    /**
     * Ingredients printed with a strength (the active-ingredient statement) come first, as one
     * combined entry for combination products, then any other recognised ingredient.
     */
    private fun nerIngredients(ingredients: List<MedicalEntity>, lines: List<Line>): List<String> {
        val withStrength = ingredients.filter { e -> strengthAfter(e, lines) != null }.map { it.value }.distinctBy { it.lowercase() }
        val others = ingredients.groupingBy { it.value }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }
        return buildList {
            if (withStrength.size > 1) add(withStrength.joinToString(" + "))
            addAll(withStrength)
            addAll(others)
        }
    }

    /** Strength printed right after an ingredient on the same line ("Paracetamol 500 mg"). */
    private fun strengthAfter(entity: MedicalEntity, lines: List<Line>): String? {
        val line = lines[entity.line]
        val tail = line.upper.substring(entity.end)
        val match = LabelDetails.strengthRegex.find(tail) ?: return null
        // Only when nothing but salt names / brackets sit between the ingredient and the amount.
        if (match.range.first > 40) return null
        return formatStrength(match)
    }

    private fun pairedStrengths(ingredients: List<MedicalEntity>, lines: List<Line>): List<String> =
        ingredients.mapNotNull { strengthAfter(it, lines) }.distinct()

    private fun brandLineStrengths(brand: String?, lines: List<Line>): List<String> {
        if (brand == null) return emptyList()
        return lines.filter { it.upper.contains(brand.uppercase(Locale.ROOT)) }
            .flatMap { line -> LabelDetails.strengthRegex.findAll(line.upper).map(::formatStrength) }
    }

    /** "Clavulanic acid (as potassium clavulanate)": the salt in brackets isn't another ingredient. */
    private fun insideBrackets(text: String, index: Int): Boolean =
        text.substring(0, index).count { it == '(' } > text.substring(0, index).count { it == ')' }

    /** Brand names the lexicon knows go first, biggest print first. */
    private fun rankNames(candidates: List<String>, brands: List<MedicalEntity>, lines: List<Line>): List<String> {
        val known = brands.sortedByDescending { lines[it.line].height }.map { it.value }
        val confirmed = candidates.filter { c -> known.any { it.equals(c, ignoreCase = true) } }
        return mergeIgnoringCase(confirmed, candidates, known).take(LabelDetails.MAX)
    }

    /** The full company name as printed around a recognised manufacturer, else the lexicon name. */
    private fun nerManufacturers(entities: List<MedicalEntity>, lines: List<Line>): List<String> =
        entities.map { e ->
            val line = lines[e.line].original
            val segment = line.split(',').firstOrNull { part -> part.uppercase(Locale.ROOT).contains(line.uppercase(Locale.ROOT).substring(e.start, e.end)) }
                ?.substringAfter(':')?.trim()
            if (segment != null && segment.length <= 60 && LabelDetails.companySuffix.containsMatchIn(segment.uppercase(Locale.ROOT))) {
                segment.toDisplayCase()
            } else {
                e.value
            }
        }

    private fun mergeIgnoringCase(vararg lists: List<String>): List<String> {
        val seen = HashSet<String>()
        return lists.asSequence().flatten().filter { it.isNotBlank() && seen.add(it.lowercase(Locale.ROOT)) }
            .take(LabelDetails.MAX).toList()
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
