package com.medishelf.core.domain.scan

import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.PackQuantity
import com.medishelf.core.model.QuantityUnit
import java.util.Locale

/** A recognised line in original and upper-case form, plus its rendered text height. */
internal data class Line(val original: String, val upper: String, val height: Int)

/** Extractors for the product details printed on a pack. Inputs are all lines from all photos. */
internal object LabelDetails {
    // Trailing lookahead, not \b: "2%" is followed by a space, and \b never matches after "%".
    val strengthRegex = Regex("""\b(\d+(?:[.,]\d+)?)\s?(MG|MCG|µG|UG|G|ML|IU|%)(?:\s?/\s?(\d+(?:[.,]\d+)?)?\s?(ML|G|DOSE|TAB))?(?![A-Z0-9])""")

    /** Removes strengths from mixed-case text (matching is done on the upper-cased copy). */
    fun stripStrengths(original: String): String {
        val sb = StringBuilder(original)
        strengthRegex.findAll(original.uppercase(Locale.ROOT)).toList().asReversed().forEach { sb.delete(it.range.first, it.range.last + 1) }
        return sb.toString().replace(Regex("""\s+"""), " ").trim(' ', '-', ',')
    }

    // --- Active ingredients -------------------------------------------------------------------

    private val ingredientKeyword = Regex(
        """\b(ACTIVE INGREDIENTS?|ACTIVE SUBSTANCES?|COMPOSITION|EACH\s+(?:[A-Z-]+\s+){0,3}?(?:TABLET|CAPSULE|CAPLET|SACHET|ML|5\s?ML|DOSE|ACTUATION|GRAM|G|VIAL|AMPOULE)S?\s+CONTAINS?)\b\s*:?""",
    )
    private val namePlusStrength = Regex("""^([A-Z][A-Z .\-]{2,40}?)\s*(?:\([^)]*\)\s*)?\d+(?:[.,]\d+)?\s?(?:MG|MCG|µG|UG|G|IU|%)(?![A-Z0-9])""")
    private val packWords = Regex("""\b(NET|CONTENTS?|TUBE|BOTTLE|PACK|EACH|PER|TOTAL|VOLUME|WEIGHT)\b""")
    private val noise = Regex("""\b(EQUIVALENT TO|EQ\.? TO|AS|USP|BP|PH\.? ?EUR|IP|EXCIPIENTS?.*|ALSO CONTAINS.*)\b""")

    /** "Paracetamol", "Amoxicillin + Clavulanic acid". Keyword sections first, then name+strength lines. */
    fun genericNames(lines: List<Line>, brand: String?): List<String> {
        val fromKeyword = mutableListOf<String>()
        lines.forEachIndexed { i, line ->
            val match = ingredientKeyword.find(line.upper) ?: return@forEachIndexed
            val rest = line.original.substring(match.range.last + 1).trim(' ', ':')
            val section = rest.ifBlank { lines.getOrNull(i + 1)?.original.orEmpty() }
            substances(section).takeIf { it.isNotEmpty() }?.let { fromKeyword += it.joinToString(" + ") }
        }
        val brandKey = brand?.uppercase(Locale.ROOT)
        val fromLines = lines.mapNotNull { line ->
            val group = namePlusStrength.find(line.upper)?.groups?.get(1) ?: return@mapNotNull null
            val upperName = group.value.trim()
            if (upperName.count(Char::isLetter) < 4 || upperName == brandKey || packWords.containsMatchIn(upperName)) return@mapNotNull null
            if (brandKey != null && upperName.startsWith(brandKey)) return@mapNotNull null
            // Same indices in the original: keep the casing as printed.
            cleanSubstance(line.original.substring(group.range.first, group.range.last + 1))
        }.distinct()
        val combined = listOfNotNull(fromLines.takeIf { it.size > 1 }?.joinToString(" + "))
        return (fromKeyword + combined + fromLines).filter(String::isNotBlank).distinct().take(MAX)
    }

    private fun substances(section: String): List<String> =
        section.split(',', ';', '+', '\n').flatMap { it.split(Regex("""\bAND\b""", RegexOption.IGNORE_CASE)) }
            .mapNotNull { part ->
                val upper = part.uppercase(Locale.ROOT)
                val beforeAmount = strengthRegex.find(upper)?.let { part.substring(0, it.range.first) } ?: part
                cleanSubstance(beforeAmount).takeIf { it.count(Char::isLetter) >= 4 }
            }
            .distinct()

    private fun cleanSubstance(raw: String): String {
        val noParens = raw.replace(Regex("""\([^)]*\)"""), " ")
        val words = noise.replace(noParens.uppercase(Locale.ROOT), " ")
        val kept = noParens.split(Regex("""\s+""")).filter { word ->
            word.isNotBlank() && word.uppercase(Locale.ROOT).let { w -> words.contains(w) && w.any(Char::isLetter) }
        }
        return kept.joinToString(" ").trim(' ', '.', ':', '-').toDisplayCase()
    }

    // --- Form ---------------------------------------------------------------------------------

    private val formPatterns = listOf(
        Regex("""\b(TABLETS?|CAPLETS?|FILM[- ]COATED)\b""") to MedicineForm.TABLET,
        Regex("""\bCAPSULES?\b""") to MedicineForm.CAPSULE,
        Regex("""\b(SYRUP|SUSPENSION|ORAL SOLUTION|ELIXIR|LINCTUS|MIXTURE)\b""") to MedicineForm.SYRUP,
        Regex("""\b(DROPS|EYE DROPS|EAR DROPS|NASAL DROPS)\b""") to MedicineForm.DROPS,
        Regex("""\b(INHALER|INHALATION|AEROSOL|EVOHALER|DISKUS|TURBUHALER|NEBULI[SZ]ER)\b""") to MedicineForm.INHALER,
        Regex("""\b(INJECTION|INJECTABLE|PRE-?FILLED|PEN|VIALS?|AMPOULES?|SYRINGES?)\b""") to MedicineForm.INJECTION,
        Regex("""\b(CREAM|OINTMENT|GEL|LOTION|PASTE)\b""") to MedicineForm.CREAM,
    )

    /** Forms by how often they appear, ties broken by first appearance. */
    fun forms(lines: List<Line>): List<MedicineForm> {
        val text = lines.joinToString("\n") { it.upper }
        return formPatterns.mapNotNull { (regex, form) ->
            val hits = regex.findAll(text).toList()
            if (hits.isEmpty()) null else Triple(form, hits.size, hits.first().range.first)
        }.sortedWith(compareByDescending<Triple<MedicineForm, Int, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
            .take(MAX)
    }

    // --- Pack quantity ------------------------------------------------------------------------

    private const val MODIFIERS = """(?:(?:FILM[- ]COATED|COATED|CHEWABLE|EFFERVESCENT|DISPERSIBLE|HARD|SOFT|GASTRO[- ]RESISTANT|PROLONGED[- ]RELEASE|MODIFIED[- ]RELEASE|ORODISPERSIBLE)\s+)*"""
    private val multiPack = Regex("""\b(\d{1,3})\s*[X×]\s*(\d{1,3})\s*$MODIFIERS(TABLETS?|CAPSULES?|CAPLETS?)\b""")
    private val countPack = Regex("""\b(\d{1,4})\s*$MODIFIERS(TABLETS?|CAPSULES?|CAPLETS?|SACHETS?|SUPPOSITORIES|PATCHES|LOZENGES|PESSARIES)\b""")
    private val dosePack = Regex("""\b(\d{1,4})\s*(?:METERED\s+)?(DOSES|ACTUATIONS|PUFFS)\b""")
    private val volumePack = Regex("""(?<![/\d.])\b(\d{1,4}(?:[.,]\d+)?)\s?ML\b""")
    private val weightPack = Regex("""(?<![/\d.])\b(\d{1,4}(?:[.,]\d+)?)\s?G\b(?!\s?/)""")
    private val dosageContext = Regex("""\b(TAKE|DOSE|SPOON|EVERY|DAILY|TIMES|HOURS?|MAX|PER DAY|CONTAINS|EACH|PER)\b""")
    private val packContext = Regex("""\b(NET|CONTENTS?|BOTTLE|TUBE|PACK|VOL|VOLUME|E\b|WHEN RECONSTITUTED)""")

    fun quantities(lines: List<Line>): List<PackQuantity> {
        val scored = mutableListOf<Pair<PackQuantity, Int>>()
        lines.forEach { line ->
            val text = line.upper
            if (dosageContext.containsMatchIn(text) && !packContext.containsMatchIn(text)) return@forEach
            multiPack.findAll(text).forEach {
                val count = it.groupValues[1].toDouble() * it.groupValues[2].toDouble()
                scored += PackQuantity(count, unitFor(it.groupValues[3])) to 30
            }
            countPack.findAll(text).forEach {
                scored += PackQuantity(it.groupValues[1].toDouble(), unitFor(it.groupValues[2])) to 20
            }
            dosePack.findAll(text).forEach { scored += PackQuantity(it.groupValues[1].toDouble(), QuantityUnit.DOSES) to 20 }
            val packBonus = if (packContext.containsMatchIn(text)) 10 else 0
            if (!LabelDetails.strengthRegex.findAll(text).any { m -> m.groupValues[4].isNotEmpty() }) {
                volumePack.findAll(text).forEach {
                    scored += PackQuantity(it.groupValues[1].replace(',', '.').toDouble(), QuantityUnit.ML) to 10 + packBonus
                }
                weightPack.findAll(text).forEach {
                    scored += PackQuantity(it.groupValues[1].replace(',', '.').toDouble(), QuantityUnit.GRAMS) to 5 + packBonus
                }
            }
        }
        return scored.filter { it.first.amount > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(MAX)
    }

    private fun unitFor(word: String): QuantityUnit = when {
        word.startsWith("CAPSULE") -> QuantityUnit.CAPSULES
        word.startsWith("TABLET") || word.startsWith("CAPLET") -> QuantityUnit.TABLETS
        else -> QuantityUnit.PIECES
    }

    // --- Manufacturer -------------------------------------------------------------------------

    private val manufacturerKeyword = Regex(
        """\b(MANUFACTURED (?:BY|FOR)|MANUFACTURER|MFD\.?\s?BY|MFG\.?\s?BY|MARKETED BY|MARKETING AUTHORI[SZ]ATION HOLDER|MAH|MADE BY|PRODUCED BY|DISTRIBUTED BY|IMPORTED BY)\b\s*:?""",
    )
    val companySuffix = Regex("""\b(LTD|LIMITED|PLC|INC|GMBH|AG|S\.?A\.?|S\.?P\.?A\.?|LLC|CORP|CORPORATION|PHARMA|PHARMACEUTICALS?|LABORATOR(?:Y|IES)|LABS|HEALTHCARE)\b\.?""")

    fun manufacturers(lines: List<Line>): List<String> {
        val fromKeyword = lines.mapIndexedNotNull { i, line ->
            val match = manufacturerKeyword.find(line.upper) ?: return@mapIndexedNotNull null
            val rest = line.original.substring(match.range.last + 1).trim(' ', ':', '-')
            companyName(rest.ifBlank { lines.getOrNull(i + 1)?.original.orEmpty() })
        }
        val fromSuffix = lines.filter { companySuffix.containsMatchIn(it.upper) && !LabelDates.expiryKeywords.containsMatchIn(it.upper) }
            .mapNotNull { companyName(it.original) }
        return (fromKeyword + fromSuffix).filter { it.count(Char::isLetter) >= 3 }.distinct().take(MAX)
    }

    /** Company name without the address that usually follows the first comma. */
    private fun companyName(text: String): String? {
        val head = text.split(',').first().trim(' ', '.', ':')
        return head.takeIf { it.isNotBlank() && it.length <= 60 }?.toDisplayCase()
    }

    // --- Prescription status ------------------------------------------------------------------

    private val prescriptionOnly = Regex(
        """(\bRX\s*ONLY\b|℞|\bPRESCRIPTION[- ]ONLY\b|\bONLY ON (?:A |MEDICAL )?PRESCRIPTION\b|\bPOM\b|\bSCHEDULE\s+(?:H1?|G|X)\b|\bTO BE SOLD (?:BY RETAIL )?ON (?:THE )?PRESCRIPTION\b|\bDISPENSED ONLY\b)""",
    )

    fun isPrescription(lines: List<Line>): Boolean = lines.any { prescriptionOnly.containsMatchIn(it.upper) }

    fun isPrescriptionLine(upper: String) = prescriptionOnly.containsMatchIn(upper)

    // --- Dosage directions and storage --------------------------------------------------------

    private val dosageKeyword = Regex(
        """^(DOSAGE(?:\s+AND\s+ADMINISTRATION)?|DOSE|DIRECTIONS(?:\s+FOR\s+USE)?|POSOLOGY|HOW TO (?:USE|TAKE)|RECOMMENDED DOSE)\b\s*:?\s*""",
    )
    private val dosageStart = Regex("""^(ADULTS?\b|CHILDREN\b|TAKE\s|APPLY\s|INHALE\s|INSTIL|INSERT\s|USE\s+\d|SWALLOW\s|DISSOLVE\s|\d+\s*(?:TO|-)\s*\d+\s+(?:TABLETS?|CAPSULES?|PUFFS?))""")

    fun dosageInstructions(lines: List<Line>): List<String> {
        val results = mutableListOf<String>()
        lines.forEachIndexed { i, line ->
            val keyword = dosageKeyword.find(line.upper)
            if (keyword == null && !dosageStart.containsMatchIn(line.upper)) return@forEachIndexed
            var text = keyword?.let { line.original.substring(it.range.last + 1) } ?: line.original
            if (text.isBlank()) text = lines.getOrNull(i + 1)?.original.orEmpty()
            // A sentence that wraps onto the next printed line continues in lower case there.
            val next = lines.getOrNull(i + 1)
            if (next != null && text != next.original && !text.trimEnd().endsWith('.') &&
                next.original.firstOrNull()?.isLowerCase() == true &&
                dosageKeyword.find(next.upper) == null && !storage.containsMatchIn(next.upper)
            ) {
                text = "$text ${next.original}"
            }
            text.trim().takeIf { it.length in 8..220 }?.let { results += it.toSentenceCase() }
        }
        return results.distinct().take(MAX)
    }

    private val storage = Regex(
        """\b(STORE\s+(?:BELOW|AT|IN|BETWEEN|UNDER|UPRIGHT)|DO NOT STORE ABOVE|KEEP\s+(?:REFRIGERATED|IN A REFRIGERATOR|IN THE FRIDGE|COOL)|REFRIGERATE|DO NOT FREEZE|PROTECT FROM (?:LIGHT|MOISTURE)|\d\s?°?\s?C?\s?(?:-|TO|–)\s?\d{1,2}\s?°\s?C)""",
    )

    /** Storage sentences only, e.g. "Store below 25°C." from "Store below 25°C. Keep out of reach of children." */
    fun storageNotes(lines: List<Line>): List<String> =
        lines.flatMap { line ->
            line.original.split(Regex("""(?<=\.)\s+""")).filter { storage.containsMatchIn(it.uppercase(Locale.ROOT)) }
        }.map { it.trim().toSentenceCase() }.distinct().take(2)

    fun isStorageLine(upper: String) = storage.containsMatchIn(upper)
    fun isDosageLine(upper: String) = dosageKeyword.containsMatchIn(upper) || dosageStart.containsMatchIn(upper)
    fun isIngredientLine(upper: String) = ingredientKeyword.containsMatchIn(upper)
    fun isManufacturerLine(upper: String) = manufacturerKeyword.containsMatchIn(upper) || companySuffix.containsMatchIn(upper)

    const val MAX = 3
}

/** ALL-CAPS OCR text becomes Title Case; mixed-case text is kept as printed. */
internal fun String.toDisplayCase(): String {
    val trimmed = trim().replace(Regex("""\s+"""), " ")
    if (trimmed.any(Char::isLowerCase)) return trimmed
    return trimmed.lowercase(Locale.ROOT).split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}

/** ALL-CAPS sentences become "Sentence case."; mixed-case text is kept as printed. */
internal fun String.toSentenceCase(): String {
    val trimmed = trim().replace(Regex("""\s+"""), " ")
    if (trimmed.any(Char::isLowerCase)) return trimmed
    return trimmed.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
}
