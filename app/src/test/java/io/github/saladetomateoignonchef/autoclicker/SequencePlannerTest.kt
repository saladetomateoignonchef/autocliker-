package io.github.saladetomateoignonchef.autoclicker

import io.github.saladetomateoignonchef.autoclicker.engine.SequencePlanner
import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequencePlannerTest {
    private var nextId = 1L

    private fun point(trigger: Trigger, delayMs: Long, pressMs: Long = 40) =
        ClickPoint(id = nextId++, x = 100, y = 200, trigger = trigger, delayMs = delayMs, pressMs = pressMs)

    @Test
    fun offsets_followStartAndPreviousTriggers() {
        val points = listOf(
            point(Trigger.AT_START, 0),
            point(Trigger.AT_START, 500),
            point(Trigger.AFTER_PREVIOUS, 200),
            point(Trigger.AFTER_PREVIOUS, 0),
            point(Trigger.AT_START, 100),
        )
        assertEquals(listOf(0L, 500L, 700L, 700L, 100L), SequencePlanner.offsets(points))
    }

    @Test
    fun offsets_firstPointHasNoPreviousSoCountsFromStart() {
        val points = listOf(point(Trigger.AFTER_PREVIOUS, 300))
        assertEquals(listOf(300L), SequencePlanner.offsets(points))
    }

    @Test
    fun plan_simultaneousPointsShareOneGesture() {
        val points = List(3) { point(Trigger.AT_START, 0) }
        val plan = SequencePlanner.plan(points, maxStrokes = 10, maxGestureMs = 60_000)
        assertEquals(1, plan.batches.size)
        assertEquals(0L, plan.batches[0].startMs)
        assertEquals(listOf(0L, 0L, 0L), plan.batches[0].taps.map { it.startMs })
        assertEquals(40L, plan.durationMs)
    }

    @Test
    fun plan_distantPointsGetSeparateGesturesAtTheirOwnTime() {
        val points = listOf(point(Trigger.AT_START, 0), point(Trigger.AFTER_PREVIOUS, 2_000))
        val plan = SequencePlanner.plan(points, maxStrokes = 10, maxGestureMs = 60_000)
        assertEquals(listOf(0L, 2_000L), plan.batches.map { it.startMs })
        assertEquals(2_040L, plan.durationMs)
    }

    @Test
    fun plan_overlappingPressesAreMergedWithRelativeStartTimes() {
        val points = listOf(
            point(Trigger.AT_START, 1_000, pressMs = 500),
            point(Trigger.AT_START, 1_200, pressMs = 100),
        )
        val plan = SequencePlanner.plan(points, maxStrokes = 10, maxGestureMs = 60_000)
        assertEquals(1, plan.batches.size)
        assertEquals(1_000L, plan.batches[0].startMs)
        assertEquals(listOf(0L, 200L), plan.batches[0].taps.map { it.startMs })
        assertEquals(1_500L, plan.durationMs)
    }

    @Test
    fun plan_pointsAreOrderedByFireTimeNotByListOrder() {
        val points = listOf(point(Trigger.AT_START, 5_000), point(Trigger.AT_START, 1_000))
        val plan = SequencePlanner.plan(points, maxStrokes = 10, maxGestureMs = 60_000)
        assertEquals(listOf(1_000L, 5_000L), plan.batches.map { it.startMs })
    }

    @Test
    fun plan_fullGesturePushesTheNextOneAfterItsEnd() {
        val points = List(3) { point(Trigger.AT_START, 0) }
        val plan = SequencePlanner.plan(points, maxStrokes = 2, maxGestureMs = 60_000)
        assertEquals(2, plan.batches.size)
        assertEquals(2, plan.batches[0].taps.size)
        val firstEnd = plan.batches[0].startMs + plan.batches[0].durationMs
        assertTrue(plan.batches[1].startMs >= firstEnd)
    }

    @Test
    fun plan_emptySequenceHasNoGesture() {
        val plan = SequencePlanner.plan(emptyList(), maxStrokes = 10, maxGestureMs = 60_000)
        assertTrue(plan.batches.isEmpty())
        assertEquals(0L, plan.durationMs)
    }
}
