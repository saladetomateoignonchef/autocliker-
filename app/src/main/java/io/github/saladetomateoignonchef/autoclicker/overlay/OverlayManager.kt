package io.github.saladetomateoignonchef.autoclicker.overlay

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import io.github.saladetomateoignonchef.autoclicker.R
import io.github.saladetomateoignonchef.autoclicker.data.ConfigStore
import io.github.saladetomateoignonchef.autoclicker.databinding.OverlayPanelBinding
import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Config
import io.github.saladetomateoignonchef.autoclicker.service.AutoClickService
import io.github.saladetomateoignonchef.autoclicker.service.StateBus
import io.github.saladetomateoignonchef.autoclicker.ui.MainActivity
import io.github.saladetomateoignonchef.autoclicker.ui.PointDialog
import kotlin.math.hypot

/** Gère les fenêtres flottantes : panneau de commande et pastilles des points de clic. */
class OverlayManager(private val service: AutoClickService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val themed = ContextThemeWrapper(service, R.style.Theme_AutoClicker)
    private val density = service.resources.displayMetrics.density
    private val markerSize = (MARKER_SIZE_DP * density).toInt()
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private var panel: OverlayPanelBinding? = null
    private val markers = LinkedHashMap<Long, MarkerView>()
    private var draggingId: Long? = null
    private var screenKeeper: View? = null
    private var running = false

    val isShown: Boolean get() = panel != null

    @get:VisibleForTesting
    internal val panelView: View? get() = panel?.root

    fun show() {
        if (isShown) return
        val binding = OverlayPanelBinding.inflate(LayoutInflater.from(themed))
        val params = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            noLimits = false,
        ).apply {
            x = (8 * density).toInt()
            y = (120 * density).toInt()
        }
        binding.playButton.setOnClickListener { service.toggleRun() }
        binding.addButton.setOnClickListener { service.addPoint() }
        binding.settingsButton.setOnClickListener {
            service.startActivity(
                Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        binding.closeButton.setOnClickListener {
            // Sans panneau, plus de bouton d'arrêt à l'écran : on arrête la séquence avec lui.
            service.engine.stop()
            hide()
        }
        makePanelDraggable(binding.dragHandle, binding.root, params)
        windowManager.addView(binding.root, params)
        panel = binding
        applyRunningState()
        sync(ConfigStore.get(service))
        StateBus.notifyChanged()
    }

    fun hide() {
        val binding = panel ?: return
        windowManager.removeView(binding.root)
        panel = null
        markers.values.forEach { windowManager.removeView(it) }
        markers.clear()
        draggingId = null
        StateBus.notifyChanged()
    }

    fun destroy() {
        hide()
        setKeepScreenOn(false)
    }

    /** Aligne les pastilles affichées sur la configuration. */
    fun sync(config: Config) {
        if (!isShown) return
        val ids = config.points.map { it.id }.toSet()
        val removed = markers.keys.filter { it !in ids }
        for (id in removed) {
            markers.remove(id)?.let { windowManager.removeView(it) }
        }
        config.points.forEachIndexed { index, point ->
            val existing = markers[point.id]
            if (existing == null) {
                addMarker(point, index + 1)
            } else {
                existing.number = index + 1
                if (draggingId != point.id) {
                    val params = existing.layoutParams as WindowManager.LayoutParams
                    val x = point.x - markerSize / 2
                    val y = point.y - markerSize / 2
                    if (params.x != x || params.y != y) {
                        params.x = x
                        params.y = y
                        windowManager.updateViewLayout(existing, params)
                    }
                }
            }
        }
    }

    fun setRunning(running: Boolean) {
        this.running = running
        applyRunningState()
    }

    /**
     * Pendant l'exécution, les pastilles laissent passer les appuis : sinon les clics injectés
     * tomberaient sur elles au lieu d'atteindre l'application en dessous.
     */
    private fun applyRunningState() {
        panel?.playButton?.apply {
            setImageResource(if (running) R.drawable.ic_stop else R.drawable.ic_play)
            contentDescription =
                service.getString(if (running) R.string.action_stop else R.string.action_start)
        }
        panel?.addButton?.isEnabled = !running
        panel?.addButton?.alpha = if (running) 0.4f else 1f
        for (marker in markers.values) applyRunningState(marker)
    }

    private fun applyRunningState(marker: MarkerView) {
        val params = marker.layoutParams as WindowManager.LayoutParams
        val flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = if (running) params.flags or flag else params.flags and flag.inv()
        marker.alpha = if (running) 0.35f else 1f
        windowManager.updateViewLayout(marker, params)
    }

    /** Fenêtre invisible d'un pixel qui empêche la mise en veille de l'écran. */
    fun setKeepScreenOn(keep: Boolean) {
        if (keep == (screenKeeper != null)) return
        if (keep) {
            val view = View(service)
            val params = baseParams(1, 1, noLimits = false).apply {
                flags = flags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            }
            windowManager.addView(view, params)
            screenKeeper = view
        } else {
            screenKeeper?.let { windowManager.removeView(it) }
            screenKeeper = null
        }
    }

    private fun baseParams(width: Int, height: Int, noLimits: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (noLimits) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Origine = coin de l'écran physique, même avec une encoche.
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            Point().also { windowManager.defaultDisplay.getRealSize(it) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addMarker(point: ClickPoint, number: Int) {
        val marker = MarkerView(themed)
        marker.number = number
        val params = baseParams(markerSize, markerSize, noLimits = true).apply {
            x = point.x - markerSize / 2
            y = point.y - markerSize / 2
        }
        val id = point.id
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        marker.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop) {
                        dragging = true
                        draggingId = id
                    }
                    if (dragging) {
                        // Le centre de la pastille (= le point cliqué) reste dans l'écran.
                        val screen = screenSize()
                        val half = markerSize / 2
                        params.x = (startX + dx.toInt()).coerceIn(-half, screen.x - half - 1)
                        params.y = (startY + dy.toInt()).coerceIn(-half, screen.y - half - 1)
                        windowManager.updateViewLayout(marker, params)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        draggingId = null
                        val cx = params.x + markerSize / 2
                        val cy = params.y + markerSize / 2
                        ConfigStore.update(service) { config ->
                            config.copy(points = config.points.map {
                                if (it.id == id) it.copy(x = cx, y = cy) else it
                            })
                        }
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        PointDialog.show(themed, id, asOverlay = true)
                    }
                }
            }
            true
        }
        windowManager.addView(marker, params)
        markers[id] = marker
        if (running) applyRunningState(marker)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makePanelDraggable(handle: View, root: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                }

                MotionEvent.ACTION_MOVE -> {
                    val screen = screenSize()
                    params.x = (startX + (event.rawX - downX).toInt())
                        .coerceIn(0, maxOf(0, screen.x - root.width))
                    params.y = (startY + (event.rawY - downY).toInt())
                        .coerceIn(0, maxOf(0, screen.y - root.height))
                    windowManager.updateViewLayout(root, params)
                }
            }
            true
        }
    }

    companion object {
        private const val MARKER_SIZE_DP = 52
    }
}
