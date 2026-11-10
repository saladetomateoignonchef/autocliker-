package io.github.saladetomateoignonchef.autoclicker.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.WindowManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.saladetomateoignonchef.autoclicker.R
import io.github.saladetomateoignonchef.autoclicker.data.ConfigStore
import io.github.saladetomateoignonchef.autoclicker.databinding.DialogPointBinding
import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Trigger

/** Réglages d'un point : déclenchement, délai, durée d'appui, suppression. */
object PointDialog {

    /**
     * [context] doit porter le thème de l'application. Avec [asOverlay], la boîte de dialogue
     * s'affiche par-dessus les autres applications (contexte du service d'accessibilité requis).
     */
    fun show(context: Context, pointId: Long, asOverlay: Boolean) {
        val config = ConfigStore.get(context)
        val index = config.points.indexOfFirst { it.id == pointId }
        if (index < 0) return
        val point = config.points[index]
        val binding = DialogPointBinding.inflate(LayoutInflater.from(context))

        fun updateDelayLabel() {
            binding.delayInput.label = if (binding.triggerPrevious.isChecked) {
                context.getString(R.string.delay_after_point, index)
            } else {
                context.getString(R.string.delay_after_start)
            }
        }

        val hasPrevious = index > 0
        binding.triggerPrevious.isEnabled = hasPrevious
        if (hasPrevious) {
            binding.triggerPrevious.text = context.getString(R.string.trigger_previous_n, index)
        }
        if (point.trigger == Trigger.AFTER_PREVIOUS && hasPrevious) {
            binding.triggerPrevious.isChecked = true
        } else {
            binding.triggerStart.isChecked = true
        }
        binding.triggerGroup.setOnCheckedChangeListener { _, _ -> updateDelayLabel() }
        updateDelayLabel()
        binding.delayInput.setValueMs(point.delayMs)
        binding.pressInput.label = context.getString(R.string.press_duration)
        binding.pressInput.setValueMs(point.pressMs)
        binding.positionText.text = context.getString(R.string.point_position, point.x, point.y)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.point_title, index + 1))
            .setView(binding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val trigger =
                    if (binding.triggerPrevious.isChecked) Trigger.AFTER_PREVIOUS else Trigger.AT_START
                val delay = (binding.delayInput.valueMs ?: point.delayMs)
                    .coerceIn(0, ClickPoint.MAX_DELAY_MS)
                val press = (binding.pressInput.valueMs ?: point.pressMs)
                    .coerceIn(1, ClickPoint.MAX_PRESS_MS)
                ConfigStore.update(context) { c ->
                    c.copy(points = c.points.map {
                        if (it.id == pointId) {
                            it.copy(trigger = trigger, delayMs = delay, pressMs = press)
                        } else {
                            it
                        }
                    })
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setNeutralButton(R.string.action_delete) { _, _ ->
                ConfigStore.update(context) { c ->
                    c.copy(points = c.points.filterNot { it.id == pointId })
                }
            }
            .create()
        if (asOverlay) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        }
        dialog.show()
    }
}
