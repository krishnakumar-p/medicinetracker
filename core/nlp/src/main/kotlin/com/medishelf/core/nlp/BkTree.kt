package com.medishelf.core.nlp

/**
 * Burkhard-Keller tree over Levenshtein distance: finds all keys within an edit distance
 * without scanning the whole lexicon. Levenshtein (not OSA) because the tree needs a metric.
 */
internal class BkTree {
    private class Node(val key: String) {
        val children = HashMap<Int, Node>()
    }

    private var root: Node? = null

    fun add(key: String) {
        val r = root ?: run { root = Node(key); return }
        var node = r
        while (true) {
            val d = levenshtein(key, node.key, Int.MAX_VALUE)
            if (d == 0) return
            node = node.children[d] ?: run { node.children[d] = Node(key); return }
        }
    }

    /** Best match within [maxDistance], or null. Ties go to the first found. */
    fun nearest(query: String, maxDistance: Int): Pair<String, Int>? {
        val r = root ?: return null
        var best: Pair<String, Int>? = null
        val stack = ArrayDeque<Node>().apply { add(r) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            // Exact distance: it also picks which branches to visit, so it must not be capped.
            val d = levenshtein(query, node.key, Int.MAX_VALUE)
            val limit = best?.second ?: maxDistance
            if (d <= limit && (best == null || d < best.second)) best = node.key to d
            val radius = best?.second ?: maxDistance
            for ((edge, child) in node.children) if (edge in (d - radius)..(d + radius)) stack.add(child)
        }
        return best
    }

    companion object {
        /** Edit distance, giving up early (returning [cap]) once it must exceed [cap] - 1. */
        fun levenshtein(a: String, b: String, cap: Int): Int {
            if (kotlin.math.abs(a.length - b.length) >= cap) return cap
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                var rowMin = curr[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                    if (curr[j] < rowMin) rowMin = curr[j]
                }
                if (rowMin >= cap) return cap
                val t = prev; prev = curr; curr = t
            }
            return minOf(prev[b.length], cap)
        }
    }
}
