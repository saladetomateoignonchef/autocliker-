package io.github.saladetomateoignonchef.autoclicker.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.saladetomateoignonchef.autoclicker.R
import io.github.saladetomateoignonchef.autoclicker.service.AutoClickService

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_TRIGGER) return
        val service = AutoClickService.instance
        if (service != null) {
            service.onScheduledTrigger()
        } else {
            Toast.makeText(context, R.string.toast_schedule_service_off, Toast.LENGTH_LONG).show()
        }
        Scheduler.onFired(context)
    }
}
