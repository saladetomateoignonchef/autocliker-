package io.github.saladetomateoignonchef.autoclicker.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import io.github.saladetomateoignonchef.autoclicker.R
import io.github.saladetomateoignonchef.autoclicker.data.ConfigStore
import io.github.saladetomateoignonchef.autoclicker.databinding.ActivityMainBinding
import io.github.saladetomateoignonchef.autoclicker.databinding.ItemPointBinding
import io.github.saladetomateoignonchef.autoclicker.engine.SequencePlanner
import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Config
import io.github.saladetomateoignonchef.autoclicker.model.Schedule
import io.github.saladetomateoignonchef.autoclicker.model.Trigger
import io.github.saladetomateoignonchef.autoclicker.schedule.Scheduler
import io.github.saladetomateoignonchef.autoclicker.service.AutoClickService
import io.github.saladetomateoignonchef.autoclicker.service.StateBus
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    /** Vrai pendant que render() écrit dans les vues, pour ignorer leurs écouteurs. */
    private var rendering = false

    private val configListener: (Config) -> Unit = { render() }
    private val stateListener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding.serviceButton.setOnClickListener { openAccessibilitySettings() }
        binding.panelButton.setOnClickListener { togglePanel() }
        binding.stopButton.setOnClickListener { AutoClickService.instance?.engine?.stop() }
        binding.addPointButton.setOnClickListener { addPoint() }

        binding.infiniteSwitch.setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            val count = if (checked) 0 else repeatCountFromField()
            ConfigStore.update(this) { it.copy(repeatCount = count) }
        }
        binding.repeatCountEdit.doAfterTextChanged {
            if (rendering || binding.infiniteSwitch.isChecked) return@doAfterTextChanged
            val count = it?.toString()?.toIntOrNull() ?: return@doAfterTextChanged
            if (count >= 1) ConfigStore.update(this) { c -> c.copy(repeatCount = count) }
        }
        binding.pauseInput.label = getString(R.string.cycle_pause)
        binding.pauseInput.onValueChanged = { ms ->
            if (!rendering) {
                val pause = ms.coerceIn(0, ClickPoint.MAX_DELAY_MS)
                ConfigStore.update(this) { it.copy(cyclePauseMs = pause) }
            }
        }

        binding.scheduleSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) updateSchedule { it.copy(enabled = checked) }
        }
        binding.dailySwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) updateSchedule { it.copy(daily = checked) }
        }
        binding.keepScreenSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) updateSchedule { it.copy(keepScreenOn = checked) }
        }
        binding.timeButton.setOnClickListener { pickTime() }
    }

    override fun onStart() {
        super.onStart()
        ConfigStore.addListener(configListener)
        StateBus.addListener(stateListener)
        render()
    }

    override fun onStop() {
        ConfigStore.removeListener(configListener)
        StateBus.removeListener(stateListener)
        super.onStop()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun togglePanel() {
        val service = AutoClickService.instance
        if (service == null) {
            Toast.makeText(this, R.string.toast_enable_service, Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
        } else if (service.overlay.isShown) {
            service.overlay.hide()
        } else {
            service.overlay.show()
        }
    }

    private fun addPoint() {
        if (!ConfigStore.addPoint(this, resources.displayMetrics)) {
            Toast.makeText(this, R.string.toast_max_points, Toast.LENGTH_SHORT).show()
        }
    }

    private fun repeatCountFromField(): Int =
        binding.repeatCountEdit.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    private fun updateSchedule(transform: (Schedule) -> Schedule) {
        val config = ConfigStore.update(this) { it.copy(schedule = transform(it.schedule)) }
        Scheduler.sync(this, config.schedule)
        render()
    }

    private fun pickTime() {
        val schedule = ConfigStore.get(this).schedule
        TimePickerDialog(
            this,
            { _, hour, minute -> updateSchedule { it.copy(hour = hour, minute = minute) } },
            schedule.hour,
            schedule.minute,
            true,
        ).show()
    }

    private fun render() {
        rendering = true
        val config = ConfigStore.get(this)
        val service = AutoClickService.instance

        // Service et panneau flottant
        binding.statusText.setText(
            when {
                service == null -> R.string.status_service_off
                service.engine.isRunning -> R.string.status_running
                else -> R.string.status_service_on
            }
        )
        binding.stopButton.isVisible = service?.engine?.isRunning == true
        binding.serviceButton.isVisible = service == null
        binding.serviceHint.isVisible = service == null
        binding.panelButton.setText(
            if (service?.overlay?.isShown == true) R.string.action_hide_panel else R.string.action_show_panel
        )

        // Séquence
        renderPoints(config)

        // Répétition
        val infinite = config.repeatCount == 0
        if (binding.infiniteSwitch.isChecked != infinite) binding.infiniteSwitch.isChecked = infinite
        binding.repeatCountLayout.isEnabled = !infinite
        if (!infinite && binding.repeatCountEdit.text?.toString()?.toIntOrNull() != config.repeatCount) {
            binding.repeatCountEdit.setText(config.repeatCount.toString())
        } else if (binding.repeatCountEdit.text.isNullOrEmpty() && !binding.repeatCountEdit.hasFocus()) {
            binding.repeatCountEdit.setText("1")
        }
        if (binding.pauseInput.valueMs != config.cyclePauseMs && !binding.pauseInput.hasFocus()) {
            binding.pauseInput.setValueMs(config.cyclePauseMs)
        }

        // Démarrage programmé
        val schedule = config.schedule
        if (binding.scheduleSwitch.isChecked != schedule.enabled) {
            binding.scheduleSwitch.isChecked = schedule.enabled
        }
        if (binding.dailySwitch.isChecked != schedule.daily) binding.dailySwitch.isChecked = schedule.daily
        if (binding.keepScreenSwitch.isChecked != schedule.keepScreenOn) {
            binding.keepScreenSwitch.isChecked = schedule.keepScreenOn
        }
        binding.timeButton.text =
            String.format(Locale.ROOT, "%02d:%02d", schedule.hour, schedule.minute)
        binding.scheduleStatus.text = when {
            !schedule.enabled -> getString(R.string.schedule_off)
            service == null -> getString(R.string.schedule_needs_service)
            else -> {
                val next = Scheduler.nextTrigger(schedule.hour, schedule.minute, ZonedDateTime.now())
                val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.getDefault())
                getString(R.string.schedule_next, formatter.format(next))
            }
        }
        rendering = false
    }

    private fun renderPoints(config: Config) {
        val container = binding.pointsContainer
        container.removeAllViews()
        binding.emptyPointsText.isVisible = config.points.isEmpty()
        val offsets = SequencePlanner.offsets(config.points)
        val inflater = LayoutInflater.from(this)
        config.points.forEachIndexed { index, point ->
            val item = ItemPointBinding.inflate(inflater, container, false)
            item.numberText.text = (index + 1).toString()
            val delay = DurationFormat.describe(point.delayMs)
            item.titleText.text = if (point.trigger == Trigger.AFTER_PREVIOUS && index > 0) {
                getString(R.string.summary_after_previous, delay, index)
            } else {
                getString(R.string.summary_at_start, delay)
            }
            item.detailText.text = getString(
                R.string.summary_detail,
                DurationFormat.describe(offsets[index]),
                DurationFormat.describe(point.pressMs),
                point.x,
                point.y,
            )
            item.root.setOnClickListener { PointDialog.show(this, point.id, asOverlay = false) }
            container.addView(item.root)
        }
    }
}
