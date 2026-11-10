package io.github.saladetomateoignonchef.autoclicker.engine

import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Trigger
import kotlin.math.max

/** Un appui à l'intérieur d'un geste ; [startMs] est relatif au début du geste. */
data class PlannedTap(val x: Int, val y: Int, val startMs: Long, val pressMs: Long)

/** Un geste envoyé en une fois au système ; [startMs] est relatif au début de la séquence. */
data class GestureBatch(val startMs: Long, val taps: List<PlannedTap>) {
    val durationMs: Long get() = taps.maxOf { it.startMs + it.pressMs }
}

data class Plan(val batches: List<GestureBatch>, val durationMs: Long)

/**
 * Transforme la liste de points en gestes prêts à être envoyés.
 *
 * Android annule le geste en cours dès qu'un nouveau geste est envoyé. Les appuis simultanés
 * ou qui se chevauchent sont donc regroupés dans un seul geste multi-doigts.
 */
object SequencePlanner {
    /** Deux appuis séparés par moins que cette marge partent dans le même geste. */
    const val MERGE_GAP_MS = 30L

    /** Instant de déclenchement de chaque point, compté depuis le début de la séquence. */
    fun offsets(points: List<ClickPoint>): List<Long> {
        val result = ArrayList<Long>(points.size)
        for ((i, p) in points.withIndex()) {
            val delay = p.delayMs.coerceAtLeast(0)
            result += if (p.trigger == Trigger.AFTER_PREVIOUS && i > 0) result[i - 1] + delay else delay
        }
        return result
    }

    fun plan(points: List<ClickPoint>, maxStrokes: Int, maxGestureMs: Long): Plan {
        require(maxStrokes >= 1) { "maxStrokes doit être >= 1" }
        require(maxGestureMs >= 1) { "maxGestureMs doit être >= 1" }
        val offsets = offsets(points)
        val taps = points.indices
            .map { i ->
                val p = points[i]
                PlannedTap(
                    x = p.x.coerceAtLeast(0),
                    y = p.y.coerceAtLeast(0),
                    startMs = offsets[i],
                    pressMs = p.pressMs.coerceIn(1, maxGestureMs),
                )
            }
            .sortedBy { it.startMs }

        val batches = ArrayList<GestureBatch>()
        var current = ArrayList<PlannedTap>()
        var currentStart = 0L
        var currentEnd = 0L

        for (tap in taps) {
            val fits = current.isNotEmpty() &&
                current.size < maxStrokes &&
                tap.startMs <= currentEnd + MERGE_GAP_MS &&
                max(tap.startMs, currentStart) + tap.pressMs - currentStart <= maxGestureMs
            if (!fits) {
                if (current.isNotEmpty()) {
                    batches += GestureBatch(currentStart, current)
                    // Si le geste précédent est plein, le suivant attend sa fin pour ne pas l'annuler.
                    currentStart = max(tap.startMs, currentEnd + MERGE_GAP_MS)
                } else {
                    currentStart = tap.startMs
                }
                current = ArrayList()
                currentEnd = currentStart
            }
            val relative = max(tap.startMs - currentStart, 0)
            current += tap.copy(startMs = relative)
            currentEnd = max(currentEnd, currentStart + relative + tap.pressMs)
        }
        if (current.isNotEmpty()) batches += GestureBatch(currentStart, current)

        return Plan(batches, batches.maxOfOrNull { it.startMs + it.durationMs } ?: 0L)
    }
}
