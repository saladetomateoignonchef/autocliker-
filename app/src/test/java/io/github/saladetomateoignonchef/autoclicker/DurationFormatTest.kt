package io.github.saladetomateoignonchef.autoclicker

import io.github.saladetomateoignonchef.autoclicker.ui.DurationFormat
import io.github.saladetomateoignonchef.autoclicker.ui.DurationUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationFormatTest {
    @Test
    fun splitPicksReadableUnit() {
        assertEquals("0" to DurationUnit.MS, DurationFormat.split(0))
        assertEquals("40" to DurationUnit.MS, DurationFormat.split(40))
        assertEquals("1.5" to DurationUnit.S, DurationFormat.split(1_500))
        assertEquals("10" to DurationUnit.S, DurationFormat.split(10_000))
        assertEquals("2" to DurationUnit.MIN, DurationFormat.split(120_000))
        assertEquals("1234" to DurationUnit.MS, DurationFormat.split(1_234))
    }

    @Test
    fun toMsParsesDecimalsAndRejectsGarbage() {
        assertEquals(1_500L, DurationFormat.toMs("1.5", DurationUnit.S))
        assertEquals(1_500L, DurationFormat.toMs("1,5", DurationUnit.S))
        assertEquals(120_000L, DurationFormat.toMs("2", DurationUnit.MIN))
        assertNull(DurationFormat.toMs("", DurationUnit.MS))
        assertNull(DurationFormat.toMs("-3", DurationUnit.MS))
    }

    @Test
    fun splitAndToMsRoundTrip() {
        for (ms in listOf(0L, 1L, 40L, 999L, 1_000L, 1_500L, 59_900L, 60_000L, 90_000L, 3_600_000L)) {
            val (text, unit) = DurationFormat.split(ms)
            assertEquals(ms, DurationFormat.toMs(text, unit))
        }
    }

    @Test
    fun describeUsesFrenchDecimalComma() {
        assertEquals("1,5 s", DurationFormat.describe(1_500))
        assertEquals("40 ms", DurationFormat.describe(40))
    }
}
