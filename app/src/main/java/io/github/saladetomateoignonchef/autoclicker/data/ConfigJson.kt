package io.github.saladetomateoignonchef.autoclicker.data

import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Config
import io.github.saladetomateoignonchef.autoclicker.model.Schedule
import io.github.saladetomateoignonchef.autoclicker.model.Trigger
import org.json.JSONArray
import org.json.JSONObject

/** Sérialisation de la configuration (stockée en JSON dans les SharedPreferences). */
object ConfigJson {

    fun toJson(config: Config): String {
        val points = JSONArray()
        for (p in config.points) {
            points.put(
                JSONObject()
                    .put("id", p.id)
                    .put("x", p.x)
                    .put("y", p.y)
                    .put("trigger", p.trigger.name)
                    .put("delayMs", p.delayMs)
                    .put("pressMs", p.pressMs)
            )
        }
        val s = config.schedule
        return JSONObject()
            .put("points", points)
            .put("repeatCount", config.repeatCount)
            .put("cyclePauseMs", config.cyclePauseMs)
            .put(
                "schedule",
                JSONObject()
                    .put("enabled", s.enabled)
                    .put("hour", s.hour)
                    .put("minute", s.minute)
                    .put("daily", s.daily)
                    .put("keepScreenOn", s.keepScreenOn)
            )
            .toString()
    }

    /** Lecture tolérante : un JSON absent ou corrompu donne la configuration par défaut. */
    fun fromJson(text: String?): Config {
        if (text.isNullOrBlank()) return Config()
        return try {
            val root = JSONObject(text)
            val defaults = Config()
            val pointsJson = root.optJSONArray("points") ?: JSONArray()
            val points = (0 until pointsJson.length()).map { i ->
                val o = pointsJson.getJSONObject(i)
                ClickPoint(
                    id = o.getLong("id"),
                    x = o.getInt("x"),
                    y = o.getInt("y"),
                    trigger = runCatching { Trigger.valueOf(o.optString("trigger")) }
                        .getOrDefault(Trigger.AT_START),
                    delayMs = o.optLong("delayMs", 0).coerceIn(0, ClickPoint.MAX_DELAY_MS),
                    pressMs = o.optLong("pressMs", ClickPoint.DEFAULT_PRESS_MS)
                        .coerceIn(1, ClickPoint.MAX_PRESS_MS),
                )
            }
            val s = root.optJSONObject("schedule") ?: JSONObject()
            Config(
                points = points,
                repeatCount = root.optInt("repeatCount", defaults.repeatCount).coerceAtLeast(0),
                cyclePauseMs = root.optLong("cyclePauseMs", defaults.cyclePauseMs)
                    .coerceIn(0, ClickPoint.MAX_DELAY_MS),
                schedule = Schedule(
                    enabled = s.optBoolean("enabled", false),
                    hour = s.optInt("hour", 22).coerceIn(0, 23),
                    minute = s.optInt("minute", 0).coerceIn(0, 59),
                    daily = s.optBoolean("daily", false),
                    keepScreenOn = s.optBoolean("keepScreenOn", true),
                ),
            )
        } catch (e: Exception) {
            Config()
        }
    }
}
