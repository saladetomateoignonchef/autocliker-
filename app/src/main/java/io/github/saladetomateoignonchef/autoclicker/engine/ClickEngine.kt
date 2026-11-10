package io.github.saladetomateoignonchef.autoclicker.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.saladetomateoignonchef.autoclicker.model.Config
import kotlin.math.max

/** Exécute la séquence de clics via les gestes du service d'accessibilité. */
class ClickEngine(
    private val service: AccessibilityService,
    private val onRunningChanged: (Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val token = Any()
    private var cyclesDone = 0

    var isRunning = false
        private set

    /** Démarre la séquence après [startDelayMs] ; renvoie false s'il n'y a aucun point. */
    fun start(config: Config, startDelayMs: Long): Boolean {
        if (isRunning) return true
        if (config.points.isEmpty()) return false
        val plan = SequencePlanner.plan(
            config.points,
            GestureDescription.getMaxStrokeCount(),
            GestureDescription.getMaxGestureDuration(),
        )
        isRunning = true
        cyclesDone = 0
        onRunningChanged(true)
        scheduleCycle(plan, config, SystemClock.uptimeMillis() + startDelayMs)
        return true
    }

    fun stop() {
        if (!isRunning) return
        handler.removeCallbacksAndMessages(token)
        isRunning = false
        onRunningChanged(false)
    }

    private fun scheduleCycle(plan: Plan, config: Config, baseTime: Long) {
        for (batch in plan.batches) {
            handler.postAtTime({ dispatch(batch) }, token, baseTime + batch.startMs)
        }
        val endTime = baseTime + plan.durationMs
        handler.postAtTime({
            cyclesDone++
            if (config.repeatCount != 0 && cyclesDone >= config.repeatCount) {
                stop()
            } else {
                val pause = max(config.cyclePauseMs, MIN_CYCLE_PAUSE_MS)
                val next = max(endTime + pause, SystemClock.uptimeMillis())
                handler.postAtTime({ scheduleCycle(plan, config, next) }, token, next)
            }
        }, token, endTime)
    }

    private fun dispatch(batch: GestureBatch) {
        val builder = GestureDescription.Builder()
        for (tap in batch.taps) {
            val path = Path().apply {
                moveTo(tap.x.toFloat(), tap.y.toFloat())
                lineTo(tap.x.toFloat(), tap.y.toFloat())
            }
            builder.addStroke(GestureDescription.StrokeDescription(path, tap.startMs, tap.pressMs))
        }
        service.dispatchGesture(builder.build(), null, null)
    }

    companion object {
        /** Plancher entre deux passages, pour ne pas saturer le système de gestes. */
        const val MIN_CYCLE_PAUSE_MS = 10L
    }
}
