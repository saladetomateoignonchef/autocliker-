package io.github.saladetomateoignonchef.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import io.github.saladetomateoignonchef.autoclicker.R
import io.github.saladetomateoignonchef.autoclicker.data.ConfigStore
import io.github.saladetomateoignonchef.autoclicker.engine.ClickEngine
import io.github.saladetomateoignonchef.autoclicker.model.Config
import io.github.saladetomateoignonchef.autoclicker.overlay.OverlayManager
import io.github.saladetomateoignonchef.autoclicker.schedule.Scheduler

/**
 * Service d'accessibilité : c'est lui qui a le droit d'envoyer des appuis à l'écran
 * (dispatchGesture) et d'afficher le panneau flottant par-dessus les autres applications.
 */
class AutoClickService : AccessibilityService() {

    lateinit var engine: ClickEngine
        private set
    lateinit var overlay: OverlayManager
        private set

    private val configListener: (Config) -> Unit = { config ->
        overlay.sync(config)
        updateKeepScreenOn()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = ClickEngine(this) { running ->
            overlay.setRunning(running)
            updateKeepScreenOn()
            StateBus.notifyChanged()
        }
        overlay = OverlayManager(this)
        instance = this
        ConfigStore.addListener(configListener)
        // Après un redémarrage du téléphone, les alarmes sont perdues : on réarme ici.
        Scheduler.sync(this, ConfigStore.get(this).schedule)
        updateKeepScreenOn()
        StateBus.notifyChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance !== this) return
        instance = null
        ConfigStore.removeListener(configListener)
        engine.stop()
        overlay.destroy()
        StateBus.notifyChanged()
    }

    fun toggleRun() {
        if (engine.isRunning) engine.stop() else startClicking(MANUAL_START_DELAY_MS)
    }

    fun startClicking(startDelayMs: Long) {
        if (!engine.start(ConfigStore.get(this), startDelayMs)) {
            Toast.makeText(this, R.string.toast_no_points, Toast.LENGTH_SHORT).show()
        }
    }

    fun addPoint() {
        if (!ConfigStore.addPoint(this, resources.displayMetrics)) {
            Toast.makeText(this, R.string.toast_max_points, Toast.LENGTH_SHORT).show()
        }
    }

    /** Appelé par l'alarme du démarrage programmé. */
    fun onScheduledTrigger() {
        wakeScreen()
        // Le panneau donne un bouton d'arrêt, même si on l'avait masqué.
        overlay.show()
        startClicking(SCHEDULED_START_DELAY_MS)
    }

    private fun wakeScreen() {
        val power = getSystemService(PowerManager::class.java)
        @Suppress("DEPRECATION")
        val wakeLock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "autoclicker:scheduled-start",
        )
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun updateKeepScreenOn() {
        val schedule = ConfigStore.get(this).schedule
        overlay.setKeepScreenOn(engine.isRunning || (schedule.enabled && schedule.keepScreenOn))
    }

    companion object {
        /** Laisse le temps de relever le doigt du bouton ▶ : un vrai appui annule les clics injectés. */
        const val MANUAL_START_DELAY_MS = 400L

        /** Laisse le temps à l'écran de se rallumer. */
        const val SCHEDULED_START_DELAY_MS = 1500L
        private const val WAKE_LOCK_TIMEOUT_MS = 10_000L

        @SuppressLint("StaticFieldLeak")
        var instance: AutoClickService? = null
            private set
    }
}
