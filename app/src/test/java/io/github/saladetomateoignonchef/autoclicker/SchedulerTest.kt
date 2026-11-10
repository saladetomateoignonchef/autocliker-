package io.github.saladetomateoignonchef.autoclicker

import io.github.saladetomateoignonchef.autoclicker.schedule.Scheduler
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulerTest {
    private val paris = ZoneId.of("Europe/Paris")

    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, 11, day, hour, minute, second, 0, paris)

    @Test
    fun laterToday() {
        assertEquals(at(10, 22, 0), Scheduler.nextTrigger(22, 0, at(10, 9, 30)))
    }

    @Test
    fun alreadyPassedGoesToTomorrow() {
        assertEquals(at(11, 2, 0), Scheduler.nextTrigger(2, 0, at(10, 9, 30)))
    }

    @Test
    fun sameMinuteGoesToTomorrow() {
        assertEquals(at(11, 22, 0), Scheduler.nextTrigger(22, 0, at(10, 22, 0, 30)))
    }
}
