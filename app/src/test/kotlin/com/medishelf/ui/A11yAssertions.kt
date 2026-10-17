package com.medishelf.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.unit.Density

/**
 * JVM-side accessibility audit (Robolectric can't run ATF): every clickable element must
 * expose a label to screen readers and offer at least a 48dp x 48dp touch target.
 */
fun SemanticsNodeInteractionsProvider.assertClickablesAccessible(screen: String, density: Density) {
    val minPx = with(density) { 48f * this.density } - 1f // tolerate rounding
    val problems = onAllNodes(hasClickAction()).fetchSemanticsNodes().mapNotNull { node ->
        val label = node.label()
        val bounds = node.touchBoundsInRoot
        when {
            // Scrolled out of view: audited when the test scrolls it in.
            node.boundsInRoot.width <= 0f || node.boundsInRoot.height <= 0f -> null
            // Partly scrolled out of view: its visible bounds are clipped, so size can't be judged.
            node.boundsInRoot.width < node.size.width - 1 || node.boundsInRoot.height < node.size.height - 1 ->
                if (label.isBlank()) "unlabeled clickable at $bounds: ${node.config}" else null
            label.isBlank() -> "unlabeled clickable at $bounds: ${node.config}"
            bounds.width < minPx || bounds.height < minPx ->
                "\"$label\" touch target ${bounds.width.toInt()}x${bounds.height.toInt()}px < 48dp"
            else -> null
        }
    }
    check(problems.isEmpty()) {
        "Accessibility problems on $screen:\n" + problems.joinToString("\n") +
            "\n--- tree ---\n" + onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                .joinToString("\n") { n -> "${n.id} ${n.boundsInRoot} children=" + n.children.joinToString { c -> c.config.toString() } }
    }
}

private fun SemanticsNode.label(): String = buildList {
    config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
    config.getOrNull(SemanticsProperties.Text)?.map { it.text }?.let(::addAll)
    config.getOrNull(SemanticsProperties.EditableText)?.text?.let(::add)
    // Merged children (e.g. a Row with a checkbox and its text) carry the label.
    children.forEach { add(it.label()) }
}.joinToString(" ").trim()
