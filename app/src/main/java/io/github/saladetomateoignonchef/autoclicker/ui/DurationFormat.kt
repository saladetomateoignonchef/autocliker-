package io.github.saladetomateoignonchef.autoclicker.ui

import java.math.BigDecimal

enum class DurationUnit(val factorMs: Long, val label: String) {
    MS(1, "ms"),
    S(1_000, "s"),
    MIN(60_000, "min"),
}

/** Conversion entre une durée en millisecondes et sa saisie « valeur + unité ». */
object DurationFormat {

    /** Choisit l'unité la plus lisible pour afficher [ms]. */
    fun split(ms: Long): Pair<String, DurationUnit> {
        val unit = when {
            ms >= DurationUnit.MIN.factorMs && ms % 6_000 == 0L -> DurationUnit.MIN
            ms >= DurationUnit.S.factorMs && ms % 100 == 0L -> DurationUnit.S
            else -> DurationUnit.MS
        }
        val value = BigDecimal(ms).divide(BigDecimal(unit.factorMs))
        return value.stripTrailingZeros().toPlainString() to unit
    }

    /** Texte saisi + unité → millisecondes, ou null si le texte n'est pas un nombre positif. */
    fun toMs(text: String, unit: DurationUnit): Long? {
        val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.multiply(BigDecimal(unit.factorMs)).toLong()
    }

    /** Exemple : 1500 → « 1,5 s ». */
    fun describe(ms: Long): String {
        val (value, unit) = split(ms)
        return value.replace('.', ',') + " " + unit.label
    }
}
