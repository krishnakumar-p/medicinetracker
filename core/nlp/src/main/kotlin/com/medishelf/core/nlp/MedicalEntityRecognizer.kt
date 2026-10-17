package com.medishelf.core.nlp

/** A medical term found in OCR text. [value] is the lexicon spelling, which corrects OCR typos. */
data class MedicalEntity(
    val type: EntityType,
    val value: String,
    val line: Int,
    val start: Int,
    val end: Int,
    /** Edit distance to the lexicon entry; 0 = exact. */
    val distance: Int,
)

/**
 * Dictionary-based named-entity recognition for medicine packs. For each line it tries word
 * spans longest-first (up to 4 words), matching exactly, then fuzzily for OCR errors, and never
 * lets two entities overlap. Runs fully on-device.
 */
class MedicalEntityRecognizer(private val lexicon: MedicineLexicon = MedicineLexicon.default) {

    private val tokenRegex = Regex("""[\p{L}\p{N}'’]+""")
    private val typeOrder = listOf(EntityType.INGREDIENT, EntityType.MANUFACTURER, EntityType.BRAND)

    fun recognize(lines: List<String>): List<MedicalEntity> = lines.flatMapIndexed { index, line -> recognizeLine(index, line) }

    private fun recognizeLine(lineIndex: Int, line: String): List<MedicalEntity> {
        val tokens = tokenRegex.findAll(line).map { m ->
            Token(TextNormalizer.key(TextNormalizer.repairToken(m.value)), m.range.first, m.range.last + 1)
        }.filter { it.key.isNotEmpty() }.toList()
        val claimed = BooleanArray(tokens.size)
        val found = mutableListOf<MedicalEntity>()
        for (size in MAX_SPAN downTo 1) {
            for (startIdx in 0..tokens.size - size) {
                val endIdx = startIdx + size
                if ((startIdx until endIdx).any { claimed[it] }) continue
                val span = tokens.subList(startIdx, endIdx)
                if (span.all { it.key in CommonWords.set || it.key.any(Char::isDigit) }) continue
                val key = span.joinToString(" ") { it.key }
                val match = exactMatch(key) ?: fuzzyMatch(key, size) ?: continue
                // Drop fragments of legal suffixes split by punctuation ("Novo Nordisk A/S" -> "Novo Nordisk").
                val printed = line.substring(span.first().start, span.last().end).replace(Regex("""(\s+\S)+$"""), "")
                found += MedicalEntity(
                    type = match.first,
                    value = displayValue(printed, span, match.second, match.third),
                    line = lineIndex,
                    start = span.first().start,
                    end = span.last().end,
                    distance = match.third,
                )
                (startIdx until endIdx).forEach { claimed[it] = true }
            }
        }
        return found.sortedBy { it.start }
    }

    /**
     * Exact matches keep the casing printed on the pack unless it's all caps. Fuzzy matches use
     * the lexicon spelling (that's the OCR correction), except when only a trailing salt word
     * differs ("Cetirizine Dihydrochloride" vs lexicon "Cetirizine Hydrochloride"): then the
     * printed form is kept, since the pack is more specific than the dictionary.
     */
    private fun displayValue(printed: String, span: List<Token>, lexiconValue: String, distance: Int): String {
        if (distance == 0) return if (printed.any(Char::isLowerCase)) printed.wordCase() else lexiconValue
        val sameFirstWord = span.size > 1 && TextNormalizer.key(lexiconValue).substringBefore(' ') == span.first().key
        return if (sameFirstWord) printed.toDisplayCase() else lexiconValue
    }

    private fun exactMatch(key: String): Triple<EntityType, String, Int>? =
        typeOrder.firstNotNullOfOrNull { type -> lexicon.exact(type, key)?.let { Triple(type, it, 0) } }

    private fun fuzzyMatch(key: String, words: Int): Triple<EntityType, String, Int>? {
        if (words > 3 || key.length < MedicineLexicon.MIN_FUZZY_LENGTH || key.any(Char::isDigit)) return null
        val maxDistance = if (key.length <= 7) 1 else 2
        return typeOrder
            // Brands are short and numerous: only allow the closest matches.
            .mapNotNull { type ->
                val limit = if (type == EntityType.BRAND) minOf(1, maxDistance) else maxDistance
                lexicon.fuzzy(type, key, limit)?.let { Triple(type, it.first, it.second) }
            }
            .minByOrNull { it.third }
    }

    /** Title-cases shouted words inside otherwise readable text: "PANADOL Extra" -> "Panadol Extra". */
    private fun String.wordCase(): String = split(' ').joinToString(" ") { word ->
        if (word.length >= 4 && word.none(Char::isLowerCase) && word.any(Char::isLetter)) word.lowercase().replaceFirstChar(Char::titlecase) else word
    }.replaceFirstChar(Char::titlecase) // "amoxicillin" -> "Amoxicillin"

    private data class Token(val key: String, val start: Int, val end: Int)

    private companion object {
        const val MAX_SPAN = 4
    }
}
