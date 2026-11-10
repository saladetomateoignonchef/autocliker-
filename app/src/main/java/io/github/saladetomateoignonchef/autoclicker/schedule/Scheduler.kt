package io.github.saladetomateoignonchef.autoclicker.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.saladetomateoignonchef.autoclicker.data.ConfigStore
import io.github.saladetomateoignonchef.autoclicker.model.Schedule
import java.time.ZonedDateTime

/** Démarrage programmé à une heure précise, via une alarme exacte. */
object Scheduler {
    const val ACTION_TRIGGER = "io.github.saladetomateoignonchef.autoclicker.SCHEDULED_START"

    /** Prochaine occurrence de [hour]:[minute] strictement après [now]. */
    fun nextTrigger(hour: Int, minute: Int, now: ZonedDateTime): ZonedDateTime {
        val today = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Pose ou retire l'alarme selon [schedule] ; renvoie l'instant visé si elle est posée. */
    fun sync(context: Context, schedule: Schedule): ZonedDateTime? {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        alarmManager.cancel(pending)
        if (!schedule.enabled) return null

        val at = nextTrigger(schedule.hour, schedule.minute, ZonedDateTime.now())
        val millis = at.toInstant().toEpochMilli()
        val exactAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
        return at
    }

    /** À appeler quand l'alarme a sonné : la réarme pour le lendemain ou la désactive. */
    fun onFired(context: Context) {
        val config = ConfigStore.get(context)
        if (config.schedule.daily) {
            sync(context, config.schedule)
        } else {
            ConfigStore.update(context) { it.copy(schedule = it.schedule.copy(enabled = false)) }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(ACTION_TRIGGER)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
