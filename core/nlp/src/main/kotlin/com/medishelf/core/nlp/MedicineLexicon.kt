package com.medishelf.core.nlp

import java.util.Locale

enum class EntityType { INGREDIENT, BRAND, MANUFACTURER }

/**
 * Dictionaries of active ingredients, brands and manufacturers (see resources
 * medishelf/lexicon/README.md for sources), indexed for exact and fuzzy lookup.
 */
class MedicineLexicon internal constructor(entries: Map<EntityType, Collection<String>>) {

    private val exact: Map<EntityType, Map<String, String>> = entries.mapValues { (_, values) ->
        buildMap { values.forEach { display -> putIfAbsent(TextNormalizer.key(display), display.toDisplayCase()) } }
    }
    private val fuzzy: Map<EntityType, BkTree> = exact.mapValues { (_, map) ->
        BkTree().apply { map.keys.filter { it.length >= MIN_FUZZY_LENGTH }.forEach(::add) }
    }

    fun size(type: EntityType): Int = exact[type]?.size ?: 0

    /** Display form for an exact key, e.g. "AMOXICILLIN" -> "Amoxicillin". */
    fun exact(type: EntityType, key: String): String? = exact[type]?.get(key)

    /** Closest entry within [maxDistance] edits; same first letter required to limit false hits. */
    fun fuzzy(type: EntityType, key: String, maxDistance: Int): Pair<String, Int>? {
        val (match, distance) = fuzzy[type]?.nearest(key, maxDistance) ?: return null
        if (match.first() != key.first()) return null
        return exact.getValue(type).getValue(match) to distance
    }

    companion object {
        const val MIN_FUZZY_LENGTH = 5

        /** Bundled lexicon, loaded once on first use (~10k entries, a few hundred ms). */
        val default: MedicineLexicon by lazy { load() }

        private fun load(): MedicineLexicon {
            val synonyms = read("synonyms.txt").flatMap { line -> line.split('=').map(String::trim) }
            return MedicineLexicon(
                mapOf(
                    EntityType.INGREDIENT to read("fda_ingredients.txt") + read("curated_ingredients.txt") + synonyms,
                    EntityType.BRAND to (read("fda_brands.txt") + read("curated_brands.txt")).filterNot(::isCommonWord),
                    EntityType.MANUFACTURER to (read("fda_manufacturers.txt") + read("curated_manufacturers.txt")).filterNot(::isCommonWord),
                ),
            )
        }

        private fun read(name: String): List<String> {
            val stream = MedicineLexicon::class.java.getResourceAsStream("/medishelf/lexicon/$name")
                ?: error("Missing lexicon resource $name")
            return stream.bufferedReader().useLines { lines ->
                lines.map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            }
        }

        /** Single common words make poor brand/company matches ("CARE", "CLEAR", "RELIEF"). */
        private fun isCommonWord(entry: String): Boolean = TextNormalizer.key(entry) in CommonWords.set
    }
}

/** ALL-CAPS lexicon entries become Title Case; mixed-case entries keep their printed form. */
internal fun String.toDisplayCase(): String {
    if (any(Char::isLowerCase)) return this
    // Short single-word names are acronyms: GSK, MSD, UCB.
    if (length <= 4 && ' ' !in this) return this
    return lowercase(Locale.ROOT).split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}
