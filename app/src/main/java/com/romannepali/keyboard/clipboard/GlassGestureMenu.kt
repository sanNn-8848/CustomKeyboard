package com.romannepali.keyboard.clipboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.romannepali.keyboard.R
import kotlin.math.abs
import kotlin.math.roundToInt

class GestureMenuItem(
    val id: Int,
    val iconRes: Int,
    val label: String,
    val enabled: Boolean = true
)

/**
 * Compact floating glass panel with one row per action, used for gesture
 * selection. A soft accent pill slides behind the rows as the finger moves;
 * releasing while a row is lit activates it. Disabled rows are dimmed and
 * never activated. The panel is drawn manually so the pill can glide smoothly
 * between entries.
 */
class GlassGestureMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private fun dp(value: Int): Int = (density * value).toInt()
    private fun dpf(value: Float): Float = density * value

    private val MARGIN = 4f
    private val PADDING_H = 14
    private val PADDING_V = 7

    private val items = mutableListOf<GestureMenuItem>()
    private val iconCache = HashMap<Int, Drawable>()
    private var darkTheme = true

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private var labelColor = 0
    private var iconColor = 0
    private var activeIndex = 0
    private var pillContinuous = 0f
    private var animFrom = 0f
    private var animTo = 0f

    private val pillAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 140L
        interpolator = DecelerateInterpolator(2f)
        addUpdateListener {
            pillContinuous = animFrom + (animTo - animFrom) * (it.animatedValue as Float)
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setTheme(dark: Boolean) {
        darkTheme = dark
        setBackgroundResource(
            if (dark) R.drawable.bg_gesture_menu_dark else R.drawable.bg_gesture_menu_light
        )
        labelColor = resources.getColor(
            if (dark) R.color.letter_text_dark else R.color.letter_text_light, null
        )
        iconColor = resources.getColor(
            if (dark) R.color.icon_dark else R.color.icon_light, null
        )
        pillPaint.color = if (dark) 0x66FF6B4A else 0x40FF6B4A
        invalidate()
    }

    fun bind(newItems: List<GestureMenuItem>, defaultIndex: Int) {
        items.clear()
        items.addAll(newItems)
        iconCache.clear()
        activeIndex = defaultIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        pillContinuous = activeIndex.toFloat()
        pillAnimator.cancel()
        layoutParams = FrameLayout.LayoutParams(menuWidthPx(), menuHeightPx())
        invalidate()
    }

    fun menuWidthPx(): Int = dp(180)

    fun menuHeightPx(): Int = dp(ROW_HEIGHT) * items.size.coerceAtLeast(1) + dp(PADDING_V) * 2

    /** Feed the finger's Y coordinate (relative to this menu's top). */
    fun onGestureMove(yInMenu: Float) {
        if (items.isEmpty()) return
        val rowH = dp(ROW_HEIGHT)
        val continuous =
            ((yInMenu - dp(PADDING_V)) / rowH).coerceIn(0f, (items.size - 1).toFloat())
        activeIndex = continuous.roundToInt().coerceIn(0, items.size - 1)
        animatePillTo(continuous)
    }

    /** The row currently lit under the finger, or null when it is disabled. */
    fun activateItem(): GestureMenuItem? {
        val item = items.getOrNull(activeIndex) ?: return null
        return item.takeIf { it.enabled }
    }

    private fun animatePillTo(target: Float) {
        if (abs(pillContinuous - target) < 0.01f) return
        pillAnimator.cancel()
        animFrom = pillContinuous
        animTo = target
        pillAnimator.start()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha shl 24) and 0xFF000000.toInt())

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || items.isEmpty()) return
        background?.draw(canvas)

        val rowH = dp(ROW_HEIGHT)
        val padV = dp(PADDING_V)

        val pillTop = padV + (pillContinuous * rowH) + dp(3)
        val pillH = rowH - dp(6)
        canvas.drawRoundRect(
            RectF(MARGIN, pillTop, width - MARGIN, pillTop + pillH),
            dp(14).toFloat(), dp(14).toFloat(), pillPaint
        )

        items.forEachIndexed { i, item ->
            val top = padV + i * rowH
            val cy = top + rowH / 2f
            val color = if (item.enabled) labelColor else withAlpha(labelColor, 96)

            val icon = item.iconRes.let { res ->
                iconCache.getOrPut(res) {
                    ContextCompat.getDrawable(context, res)!!.mutate().apply {
                        setTint(iconColor)
                    }
                }
            }
            icon.setBounds(
                dp(PADDING_H),
                (cy - dp(10)).toInt(),
                dp(PADDING_H) + dp(20),
                (cy + dp(10)).toInt()
            )
            icon.alpha = if (item.enabled) 255 else 100
            icon.draw(canvas)

            textPaint.color = color
            textPaint.textSize = dpf(14f)
            textPaint.textAlign = Paint.Align.LEFT
            val textX = (dp(PADDING_H) + dp(26)).toFloat()
            val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(item.label, textX, baseline, textPaint)
        }
    }

    /** Fade/scale out and detach from its parent. */
    fun dismiss(onDone: (() -> Unit)? = null) {
        animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(110)
            .withEndAction {
                (parent as? android.view.ViewManager)?.removeView(this)
                onDone?.invoke()
            }
            .start()
    }

    companion object {
        const val ROW_HEIGHT = 44
    }
}