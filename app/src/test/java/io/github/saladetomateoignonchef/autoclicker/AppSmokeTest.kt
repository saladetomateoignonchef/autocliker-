package io.github.saladetomateoignonchef.autoclicker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import io.github.saladetomateoignonchef.autoclicker.data.ConfigStore
import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Trigger
import io.github.saladetomateoignonchef.autoclicker.service.AutoClickService
import io.github.saladetomateoignonchef.autoclicker.ui.DurationInputView
import io.github.saladetomateoignonchef.autoclicker.ui.MainActivity
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** Fait tourner le vrai code Android (activité, service, panneau, gestes) sur la JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        ConfigStore.resetForTests()
    }

    @After
    fun tearDown() {
        ConfigStore.resetForTests()
    }

    private fun connectService(): AutoClickService {
        val service = Robolectric.buildService(AutoClickService::class.java).create().get()
        AccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
            .apply { isAccessible = true }
            .invoke(service)
        return service
    }

    private fun setPoints(vararg points: ClickPoint, repeatCount: Int = 1) {
        ConfigStore.update(context) { it.copy(points = points.toList(), repeatCount = repeatCount) }
    }

    @Test
    fun mainScreen_addsAPointAndEditsItThroughTheDialog() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val container = activity.findViewById<LinearLayout>(R.id.pointsContainer)
        assertEquals(0, container.childCount)

        activity.findViewById<View>(R.id.addPointButton).performClick()
        activity.findViewById<View>(R.id.addPointButton).performClick()
        assertEquals(2, container.childCount)

        // Ouvre le point 2, le passe en « après le point 1 » avec 2 s de délai.
        container.getChildAt(1).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        dialog.findViewById<View>(R.id.triggerPrevious)!!.performClick()
        dialog.findViewById<DurationInputView>(R.id.delayInput)!!.setValueMs(2_000)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        ShadowLooper.idleMainLooper()

        val second = ConfigStore.get(context).points[1]
        assertEquals(Trigger.AFTER_PREVIOUS, second.trigger)
        assertEquals(2_000L, second.delayMs)
        assertEquals(2, container.childCount)
    }

    @Test
    fun mainScreen_savesRepeatAndScheduleSettings() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.infiniteSwitch).performClick()
        assertEquals(0, ConfigStore.get(context).repeatCount)
        activity.findViewById<DurationInputView>(R.id.pauseInput).apply {
            setValueMs(3_000)
            onValueChanged?.invoke(3_000)
        }
        assertEquals(3_000L, ConfigStore.get(context).cyclePauseMs)

        activity.findViewById<View>(R.id.scheduleSwitch).performClick()
        assertTrue(ConfigStore.get(context).schedule.enabled)
        val alarms = shadowOf(context.getSystemService(android.app.AlarmManager::class.java))
        assertNotNull(alarms.nextScheduledAlarm)

        activity.findViewById<View>(R.id.scheduleSwitch).performClick()
        assertFalse(ConfigStore.get(context).schedule.enabled)
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun service_showsPanelAndMarkers_thenHidesThem() {
        val service = connectService()
        assertEquals(service, AutoClickService.instance)
        service.overlay.show()
        assertTrue(service.overlay.isShown)
        service.addPoint()
        service.addPoint()
        assertEquals(2, ConfigStore.get(context).points.size)
        // Supprimer un point retire sa pastille sans erreur.
        ConfigStore.update(context) { it.copy(points = it.points.drop(1)) }
        service.overlay.hide()
        assertFalse(service.overlay.isShown)
        service.onDestroy()
        assertNull(AutoClickService.instance)
    }

    @Test
    fun engine_clicksSimultaneousPointsInOneGesture_andChainedPointLater() {
        val service = connectService()
        service.overlay.show()
        setPoints(
            ClickPoint(1, 100, 200),
            ClickPoint(2, 300, 400),
            ClickPoint(3, 500, 600, Trigger.AFTER_PREVIOUS, delayMs = 1_000),
        )
        service.toggleRun()
        assertTrue(service.engine.isRunning)

        // Juste après le délai de démarrage : les points 1 et 2 partent ensemble.
        ShadowLooper.idleMainLooper(AutoClickService.MANUAL_START_DELAY_MS + 100, TimeUnit.MILLISECONDS)
        val gestures = shadowOf(service).gesturesDispatched
        assertEquals(1, gestures.size)
        assertEquals(2, gestures[0].description().strokeCount)
        assertTrue(service.engine.isRunning)

        // Une seconde plus tard : le point 3, puis fin de l'unique passage.
        ShadowLooper.idleMainLooper(1_100, TimeUnit.MILLISECONDS)
        assertEquals(2, shadowOf(service).gesturesDispatched.size)
        assertEquals(1, shadowOf(service).gesturesDispatched[1].description().strokeCount)
        assertFalse(service.engine.isRunning)
        service.onDestroy()
    }

    @Test
    fun engine_loopsUntilStopped() {
        val service = connectService()
        setPoints(ClickPoint(1, 100, 200), repeatCount = 0)
        ConfigStore.update(context) { it.copy(cyclePauseMs = 500) }
        service.toggleRun()
        ShadowLooper.idleMainLooper(AutoClickService.MANUAL_START_DELAY_MS + 2_000, TimeUnit.MILLISECONDS)
        // Un passage toutes les 540 ms (40 ms d'appui + 500 ms de pause) pendant 2 s.
        assertEquals(4, shadowOf(service).gesturesDispatched.size)
        assertTrue(service.engine.isRunning)

        service.toggleRun()
        assertFalse(service.engine.isRunning)
        ShadowLooper.idleMainLooper(2_000, TimeUnit.MILLISECONDS)
        assertEquals(4, shadowOf(service).gesturesDispatched.size)
        service.onDestroy()
    }

    @Test
    fun scheduledTrigger_startsTheSequence_andOneShotScheduleTurnsItselfOff() {
        val service = connectService()
        setPoints(ClickPoint(1, 100, 200))
        ConfigStore.update(context) { it.copy(schedule = it.schedule.copy(enabled = true, daily = false)) }

        io.github.saladetomateoignonchef.autoclicker.schedule.ScheduleReceiver().onReceive(
            context,
            android.content.Intent(io.github.saladetomateoignonchef.autoclicker.schedule.Scheduler.ACTION_TRIGGER),
        )
        assertTrue(service.engine.isRunning)
        assertTrue(service.overlay.isShown)
        assertFalse(ConfigStore.get(context).schedule.enabled)
        ShadowLooper.idleMainLooper(AutoClickService.SCHEDULED_START_DELAY_MS + 100, TimeUnit.MILLISECONDS)
        assertEquals(1, shadowOf(service).gesturesDispatched.size)
        service.onDestroy()
    }

    @Test
    fun stopButtonOnMainScreen_stopsARunningSequence() {
        val service = connectService()
        setPoints(ClickPoint(1, 100, 200), repeatCount = 0)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val stop = activity.findViewById<View>(R.id.stopButton)
        assertEquals(View.GONE, stop.visibility)

        service.toggleRun()
        assertEquals(View.VISIBLE, stop.visibility)
        stop.performClick()
        assertFalse(service.engine.isRunning)
        assertEquals(View.GONE, stop.visibility)
        service.onDestroy()
    }

    @Test
    fun closingThePanel_stopsARunningSequence() {
        val service = connectService()
        setPoints(ClickPoint(1, 100, 200), repeatCount = 0)
        service.overlay.show()
        service.toggleRun()
        assertTrue(service.engine.isRunning)

        service.overlay.panelView!!.findViewById<View>(R.id.closeButton).performClick()
        assertFalse(service.engine.isRunning)
        assertFalse(service.overlay.isShown)
        service.onDestroy()
    }

    @Test
    fun startWithoutPoints_doesNotRun() {
        val service = connectService()
        service.toggleRun()
        assertFalse(service.engine.isRunning)
        service.onDestroy()
    }
}
