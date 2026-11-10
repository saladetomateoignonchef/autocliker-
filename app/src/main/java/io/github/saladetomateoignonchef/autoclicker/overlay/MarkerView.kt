package io.github.saladetomateoignonchef.autoclicker.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** Pastille numérotée posée à l'endroit d'un clic ; le clic part de son centre. */
class MarkerView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 25, 118, 210)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.WHITE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 18 * density
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(2 * density, 0f, 0f, Color.BLACK)
    }

    var number: Int = 1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - ring.strokeWidth
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, ring)
        canvas.drawText(number.toString(), cx, cy - (text.descent() + text.ascent()) / 2, text)
    }
}
