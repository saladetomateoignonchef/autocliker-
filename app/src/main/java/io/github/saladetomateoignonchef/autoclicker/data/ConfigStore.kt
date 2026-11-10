package io.github.saladetomateoignonchef.autoclicker.data

import android.content.Context
import android.util.DisplayMetrics
import androidx.annotation.VisibleForTesting
import io.github.saladetomateoignonchef.autoclicker.model.ClickPoint
import io.github.saladetomateoignonchef.autoclicker.model.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Configuration courante, partagée entre l'activité et le service (même processus).
 * À n'utiliser que depuis le thread principal.
 */
object ConfigStore {
    private const val PREFS = "autoclicker"
    private const val KEY = "config"

    private var cached: Config? = null
    private val listeners = CopyOnWriteArrayList<(Config) -> Unit>()

    fun get(context: Context): Config {
        cached?.let { return it }
        val text = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        return ConfigJson.fromJson(text).also { cached = it }
    }

    fun update(context: Context, transform: (Config) -> Config): Config {
        val old = get(context)
        val new = transform(old)
        if (new == old) return old
        cached = new
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, ConfigJson.toJson(new))
            .apply()
        listeners.forEach { it(new) }
        return new
    }

    /** Oublie la configuration en mémoire (tests uniquement). */
    @VisibleForTesting
    internal fun resetForTests() {
        cached = null
        listeners.clear()
    }

    fun addListener(listener: (Config) -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: (Config) -> Unit) {
        listeners.remove(listener)
    }

    /** Ajoute un point près du centre de l'écran ; renvoie false si la limite est atteinte. */
    fun addPoint(context: Context, metrics: DisplayMetrics): Boolean {
        val config = get(context)
        if (config.points.size >= Config.MAX_POINTS) return false
        val id = (config.points.maxOfOrNull { it.id } ?: 0L) + 1
        // Décalage en cascade pour que les nouveaux points ne se superposent pas.
        val step = (28 * metrics.density).toInt()
        val shift = (config.points.size % 6 - 2) * step
        val point = ClickPoint(
            id = id,
            x = metrics.widthPixels / 2 + shift,
            y = metrics.heightPixels / 2 + shift,
        )
        update(context) { it.copy(points = it.points + point) }
        return true
    }
}
