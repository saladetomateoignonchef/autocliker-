package io.github.saladetomateoignonchef.autoclicker

import io.github.saladetomateoignonchef.autoclicker.data.ConfigJson
import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Config
import io.github.saladetomateoignonchef.autoclicker.model.Schedule
import io.github.saladetomateoignonchef.autoclicker.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigJsonTest {
    @Test
    fun roundTrip() {
        val config = Config(
            points = listOf(
                ClickPoint(1, 10, 20),
                ClickPoint(2, 300, 400, Trigger.AFTER_PREVIOUS, delayMs = 1_500, pressMs = 800),
            ),
            repeatCount = 0,
            cyclePauseMs = 250,
            schedule = Schedule(enabled = true, hour = 2, minute = 5, daily = true, keepScreenOn = false),
        )
        assertEquals(config, ConfigJson.fromJson(ConfigJson.toJson(config)))
    }

    @Test
    fun missingOrCorruptedJsonGivesDefaults() {
        assertEquals(Config(), ConfigJson.fromJson(null))
        assertEquals(Config(), ConfigJson.fromJson("{pas du json"))
    }
}
