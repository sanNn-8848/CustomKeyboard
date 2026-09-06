package com.romannepali.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent

@SuppressLint("ViewConstructor")
class GboardKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : KeyboardView(context, attrs, defStyleAttr) {

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // GBoard color palette
    private val normalKeyColor = 0xFF2B2A2F.toInt()
    private val specialKeyColor = 0xFF333238.toInt()
    private val enterKeyColor = 0xFFB388FF.toInt()
    private val keyTextColor = 0xFFFFFFFF.toInt()
    private val enterTextColor = 0xFF000000.toInt()

    private val cornerRadius = 8f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val kbd = keyboard ?: return
        val padding = 2f * resources.displayMetrics.density
        val keyHeight = 52f * resources.displayMetrics.density

        for (key in kbd.keys) {
            val x = key.x.toFloat()
            val y = (key.y + key.height * 0.05f).toFloat()
            val w = (key.width - padding * 2).toFloat()
            val h = keyHeight

            val special = key.codes[0] in listOf(-1, -5, -2, -3, -6)
            val enter = key.codes[0] == -4

            val fill = when {
                enter -> enterKeyColor
                special -> specialKeyColor
                else -> normalKeyColor
            }

            keyPaint.color = fill
            canvas.drawRoundRect(x + padding, y, x + w, y + h, cornerRadius, cornerRadius, keyPaint)

            if (key.pressed) {
                pressedPaint.color = 0x33FFFFFF
                canvas.drawRoundRect(x + padding, y, x + w, y + h, cornerRadius, cornerRadius, pressedPaint)
            }

            val label = key.label?.toString() ?: continue
            if (label.isNotEmpty()) {
                labelPaint.textSize = 20f * resources.displayMetrics.density
                labelPaint.typeface = Typeface.DEFAULT
                labelPaint.color = if (enter) enterTextColor else keyTextColor
                labelPaint.textAlign = Paint.Align.CENTER
                val baseline = y + h / 2 - (labelPaint.ascent() + labelPaint.descent()) / 2
                canvas.drawText(label, x + w / 2, baseline, labelPaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Ensure keyboard is measured with its content height
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val result = super.onTouchEvent(event)
        invalidate()
        return result
    }
}
