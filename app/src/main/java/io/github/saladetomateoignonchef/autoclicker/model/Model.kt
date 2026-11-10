package io.github.saladetomateoignonchef.autoclicker.model

/** Ce qui déclenche un point de clic à l'intérieur d'une séquence. */
enum class Trigger {
    /** Le délai du point est compté depuis le démarrage de la séquence. */
    AT_START,

    /** Le délai du point est compté depuis le déclenchement du point précédent. */
    AFTER_PREVIOUS,
}

data class ClickPoint(
    val id: Long,
    /** Centre du clic, en pixels écran. */
    val x: Int,
    val y: Int,
    val trigger: Trigger = Trigger.AT_START,
    val delayMs: Long = 0,
    val pressMs: Long = DEFAULT_PRESS_MS,
) {
    companion object {
        const val DEFAULT_PRESS_MS = 40L
        const val MAX_PRESS_MS = 60_000L
        const val MAX_DELAY_MS = 24L * 60 * 60 * 1000
    }
}

data class Schedule(
    val enabled: Boolean = false,
    val hour: Int = 22,
    val minute: Int = 0,
    val daily: Boolean = false,
    val keepScreenOn: Boolean = true,
)

data class Config(
    val points: List<ClickPoint> = emptyList(),
    /** Nombre de passages de la séquence ; 0 = en boucle jusqu'à l'arrêt manuel. */
    val repeatCount: Int = 1,
    /** Pause entre la fin d'un passage et le début du suivant. */
    val cyclePauseMs: Long = 1000,
    val schedule: Schedule = Schedule(),
) {
    companion object {
        const val MAX_POINTS = 30
    }
}
