package io.github.saladetomateoignonchef.autoclicker.service

import java.util.concurrent.CopyOnWriteArrayList

/** Prévient l'interface quand l'état du service change (connecté, en marche, panneau affiché). */
object StateBus {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach { it() }
    }
}
