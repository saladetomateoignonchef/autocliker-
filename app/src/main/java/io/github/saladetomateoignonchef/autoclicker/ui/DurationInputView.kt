package io.github.saladetomateoignonchef.autoclicker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import io.github.saladetomateoignonchef.autoclicker.R
import io.github.saladetomateoignonchef.autoclicker.databinding.ViewDurationInputBinding

/** Champ de saisie d'une durée : une valeur et un choix d'unité (ms / s / min). */
class DurationInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val binding = ViewDurationInputBinding.inflate(LayoutInflater.from(context), this)
    private var suppress = false

    /** Appelé à chaque saisie valide, avec la durée en millisecondes. */
    var onValueChanged: ((Long) -> Unit)? = null

    var label: CharSequence?
        get() = binding.valueLayout.hint
        set(value) {
            binding.valueLayout.hint = value
        }

    /** Durée saisie en millisecondes, ou null si le champ est vide ou invalide. */
    val valueMs: Long?
        get() = DurationFormat.toMs(binding.valueEdit.text?.toString().orEmpty(), unit)

    private val unit: DurationUnit
        get() = when (binding.unitGroup.checkedButtonId) {
            R.id.unitMin -> DurationUnit.MIN
            R.id.unitS -> DurationUnit.S
            else -> DurationUnit.MS
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Plusieurs champs de ce type partagent les mêmes identifiants internes : on ne laisse
        // pas Android restaurer leur état, il est toujours réécrit depuis la configuration.
        binding.valueEdit.isSaveEnabled = false
        binding.unitGroup.isSaveEnabled = false
        binding.valueEdit.doAfterTextChanged { notifyChanged() }
        binding.unitGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) notifyChanged()
        }
    }

    fun setValueMs(ms: Long) {
        val (text, unit) = DurationFormat.split(ms)
        suppress = true
        binding.valueEdit.setText(text)
        binding.unitGroup.check(
            when (unit) {
                DurationUnit.MIN -> R.id.unitMin
                DurationUnit.S -> R.id.unitS
                DurationUnit.MS -> R.id.unitMs
            }
        )
        suppress = false
    }

    private fun notifyChanged() {
        if (suppress) return
        valueMs?.let { onValueChanged?.invoke(it) }
    }
}
