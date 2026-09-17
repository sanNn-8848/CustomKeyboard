package com.romannepali.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.max

/**
 * "Suggestion Edit Mode" drag layer.
 *
 * The moment a suggestion chip is long-pressed a two-cell strip POPS IN just
 * above the suggestion bar (LEFT = ⭐ FAVORITE, RIGHT = 🗑 DELETE) and the chip
 * word itself is drawn as a glowing ghost that follows the finger. Releasing
 * inside a cell performs the action with a confirm burst and the strip fades
 * out; releasing anywhere else flies the word back and the strip fades out.
 *
 * All coordinates are overlay-local (0,0 = overlay top-left).
 */
class SuggestionDragOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onRemoveRequested: ((String) -> Unit)? = null
    var onFavoriteRequested: ((String) -> Unit)? = null

    /** Fired once the drag is fully over so the caller can restore the grabbed chip. */
    var onDragFinished: (() -> Unit)? = null

    /** The strip is a full-width band directly above the suggestion bar. */
    private val stripTop = dp(6f)
    private val stripH = dp(56f)
    private val stripBottom = stripTop + stripH
    private val cellPadX = dp(12f)

    private var active = false
    private var status = STATUS_HIDDEN
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { forceHide() }

    private var word = ""
    private val origin = Rect()
    private var ghostX = 0f
    private var ghostY = 0f
    private var fingerX = 0f
    private var fingerY = 0f

    private var dropZone: Zone? = null
    private var burstProgress = 0f
    private var burstAnimator: ValueAnimator? = null

    private var returnAnimator: ValueAnimator? = null
    private var popProgress = 0f
    private var popAnimator: ValueAnimator? = null

    // Proximity haptic bookkeeping.
    private var inDrop = false

    private enum class Zone { DELETE, FAVORITE }

    /**
     * Never let the overlay influence the keyboard root's measured size:
     * 1×1 on the first pass, parent size afterwards. This stops the IME window
     * from expanding and leaving a giant dark void above the keyboard.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val p = parent as? View
        val w = if (p != null && p.width > 0) p.width else 1
        val h = if (p != null && p.height > 0) p.height else 1
        setMeasuredDimension(w, h)
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun beginDrag(draggedWord: String, from: Rect, touchX: Float, touchY: Float) {
        endCurrentDragIfAny()
        mainHandler.removeCallbacks(autoHideRunnable)
        word = draggedWord
        origin.set(from)
        fingerX = touchX
        fingerY = touchY
        // The word chip is exactly under the finger: what you see is where it drops.
        ghostX = touchX
        ghostY = touchY
        inDrop = false
        active = true
        status = STATUS_DRAG
        visibility = VISIBLE
        popStrip(true)
        // Safety net: if the gesture is lost for any reason, force-hide after 10 s.
        mainHandler.postDelayed(autoHideRunnable, 10_000L)
        haptic(Haptics.GESTURE_START)
        invalidate()
    }

    fun updateFinger(x: Float, y: Float) {
        if (!active) return
        fingerX = x
        fingerY = y
        val zone = zoneAt(x, y)
        applyProximityHaptics(zone)
        invalidate()
    }

    fun finishDrag(x: Float, y: Float) {
        if (!active) return
        fingerX = x
        fingerY = y
        val zone = zoneAt(x, y)
        if (zone != null) performDrop(zone) else flyBack()
    }

    fun cancelDrag() {
        if (!active) return
        flyBack()
    }

    /** Cleanly ends any in-flight state so a new drag can take over. */
    fun endCurrentDragIfAny() {
        if (visibility != INVISIBLE) forceHide()
    }

    fun isDragActive(): Boolean = active && status == STATUS_DRAG

    // ------------------------------------------------------------------
    // Strip geometry
    // ------------------------------------------------------------------

    private fun stripCenterX(zone: Zone): Float =
        if (zone == Zone.FAVORITE) width / 4f else width * 3f / 4f

    private fun stripCenterY(): Float = stripTop + stripH / 2f

    /** The whole strip band is the drop target: left half = FAVORITE, right half = DELETE. */
    private fun zoneAt(x: Float, y: Float): Zone? {
        if (y < stripTop || y > stripBottom) return null
        return if (x < width / 2f) Zone.FAVORITE else Zone.DELETE
    }

    private fun applyProximityHaptics(zone: Zone?) {
        val nowDrop = zone != null
        if (nowDrop && !inDrop) haptic(Haptics.MAGNET_ENTER)
        if (!nowDrop && inDrop) haptic(Haptics.MAGNET_LEAVE)
        inDrop = nowDrop
    }

    // ------------------------------------------------------------------
    // Drop / return
    // ------------------------------------------------------------------

    private fun performDrop(zone: Zone) {
        // Fire the action instantly so typing/favorites never wait on the flourish.
        when (zone) {
            Zone.DELETE -> onRemoveRequested?.invoke(word)
            Zone.FAVORITE -> onFavoriteRequested?.invoke(word)
        }
        active = false
        status = STATUS_BURST
        dropZone = zone
        burstProgress = 0f
        popStrip(false)
        haptic(Haptics.CONFIRM)
        val target = Pair(stripCenterX(zone), stripCenterY())
        burstAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            start()
            addUpdateListener {
                burstProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hide()
                }
            })
        }
        burstX = target.first
        burstY = target.second
    }

    private fun flyBack() {
        active = false
        status = STATUS_RETURN
        popStrip(false)
        val startX = ghostX
        val startY = ghostY
        val endX = origin.centerX().toFloat()
        val endY = origin.centerY().toFloat()
        returnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = OvershootInterpolator(1.2f)
            start()
            addUpdateListener {
                val t = it.animatedValue as Float
                ghostX = startX + (endX - startX) * t
                ghostY = startY + (endY - startY) * t
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hide()
                }
            })
        }
    }

    /** Pops the strip in (grow) or fades it out (shrink) with a spring. */
    private fun popStrip(grow: Boolean) {
        popAnimator?.cancel()
        popAnimator = ValueAnimator.ofFloat(popProgress, if (grow) 1f else 0f).apply {
            duration = 150
            interpolator = if (grow) OvershootInterpolator(1.15f) else DecelerateInterpolator()
            start()
            addUpdateListener {
                popProgress = it.animatedValue as Float
                invalidate()
            }
        }
    }

    private fun hide() {
        dropZone = null
        status = STATUS_HIDDEN
        active = false
        popProgress = 0f
        visibility = INVISIBLE
        mainHandler.removeCallbacks(autoHideRunnable)
        onDragFinished?.invoke()
        invalidate()
    }

    /** Bulletproof hide: clears every possible stuck state. */
    private fun forceHide() {
        cancelAnimations()
        mainHandler.removeCallbacks(autoHideRunnable)
        hide()
    }

    private fun cancelAnimations() {
        burstAnimator?.cancel()
        returnAnimator?.cancel()
        popAnimator?.cancel()
        burstAnimator = null
        returnAnimator = null
        popAnimator = null
    }

    // ------------------------------------------------------------------
    // Haptics
    // ------------------------------------------------------------------

    private enum class Haptics {
        GESTURE_START, MAGNET_ENTER, MAGNET_LEAVE, CONFIRM
    }

    private fun haptic(kind: Haptics) {
        val constant = when (kind) {
            Haptics.GESTURE_START -> HapticFeedbackConstants.GESTURE_START
            Haptics.MAGNET_ENTER -> HapticFeedbackConstants.GESTURE_START
            Haptics.MAGNET_LEAVE -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptics.CONFIRM -> HapticFeedbackConstants.CONFIRM
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(constant)
        } else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    private val scrimPaint = Paint().apply { color = Color.argb(195, 4, 7, 12) }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(22).toFloat()
    }
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = dp(9.5f).toFloat()
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
    }
    private val ghostBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val ghostTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 32, 41)
        textAlign = Paint.Align.CENTER
        textSize = dp(15).toFloat()
        typeface = Typeface.DEFAULT_BOLD
    }
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4).toFloat()
    }

    private var burstX = 0f
    private var burstY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (status == STATUS_HIDDEN) return

        // Dim the keyboard — but never the whole screen above it.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val phase = SystemClock.uptimeMillis() / 1000f * 2.5f

        // The two-cell strip, popping in only while the user is dragging.
        if (status == STATUS_DRAG || status == STATUS_RETURN || status == STATUS_BURST) {
            drawStrip(canvas, phase)
        }

        // The word chip ghost under the finger.
        if (status == STATUS_DRAG || status == STATUS_RETURN) {
            drawGhost(canvas)
        }

        // Drop burst flash.
        if (status == STATUS_BURST && dropZone != null) {
            val t = burstProgress
            val color = if (dropZone == Zone.DELETE) DELETE_RGB else FAVORITE_RGB
            burstPaint.color = Color.argb(
                ((1f - t) * 230).toInt(), Color.red(color), Color.green(color), Color.blue(color)
            )
            val radius = dp(16) + t * dp(56)
            canvas.drawCircle(burstX, burstY, radius, burstPaint)
            canvas.drawCircle(burstX, burstY, radius * 0.6f, burstPaint)
        }
    }

    private fun drawStrip(canvas: Canvas, phase: Float) {
        drawCell(canvas, 0f, width / 2f, Zone.FAVORITE)
        drawCell(canvas, width / 2f, width.toFloat(), Zone.DELETE)
    }

    private fun drawCell(canvas: Canvas, left: Float, right: Float, zone: Zone) {
        val rgb = if (zone == Zone.DELETE) DELETE_RGB else FAVORITE_RGB
        val active = zoneAt(fingerX, fingerY) == zone
        val pop = ((1f - popProgress) * 0.5f + popProgress * 1f)
        val alpha = (150 * pop).toInt()

        // Cell background.
        cellBgPaint.color = Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb))
        cellBgPaint.alpha = if (active) 190 else 90
        val inset = dp(3)
        canvas.drawRoundRect(
            left + inset, stripTop + inset,
            right - inset, stripBottom - inset,
            dp(12).toFloat(), dp(12).toFloat(), cellBgPaint
        )

        // Accent border that brightens when the chip is over this cell.
        cellBorderPaint.color = Color.argb(
            (if (active) 230 else 120) * pop.toInt(),
            Color.red(rgb), Color.green(rgb), Color.blue(rgb)
        )
        canvas.drawRoundRect(
            left + inset, stripTop + inset,
            right - inset, stripBottom - inset,
            dp(12).toFloat(), dp(12).toFloat(), cellBorderPaint
        )

        val cx = (left + right) / 2f
        val cy = stripTop + stripH / 2f
        val icon = if (zone == Zone.FAVORITE) "\u2B50" else "\uD83D\uDDD1\uFE0F"
        iconPaint.alpha = 255
        val fm = iconPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(icon, cx, baseline, iconPaint)

        labelPaint.alpha = (130 + 125 * if (active) 1f else 0f).toInt()
        canvas.drawText(
            if (zone == Zone.FAVORITE) context.getString(R.string.suggestion_favorite_zone)
            else context.getString(R.string.suggestion_remove_zone),
            cx,
            cy + dp(16),
            labelPaint
        )
    }

    private fun drawGhost(canvas: Canvas) {
        val gx = ghostX
        val gy = ghostY
        val zone = zoneAt(fingerX, fingerY)
        val textW = ghostTextPaint.measureText(word)
        val w = max(dp(64).toFloat(), textW + dp(40).toFloat())
        val h = dp(42).toFloat()
        val l = gx - w / 2f
        val t = gy - h / 2f
        val radius = dp(12).toFloat()

        ghostBgPaint.setShadowLayer(dp(10).toFloat(), 0f, dp(6).toFloat(), Color.argb(110, 0, 0, 0))
        canvas.drawRoundRect(l, t, l + w, t + h, radius, radius, ghostBgPaint)

        // Accent outline reacts to whichever zone the word is over.
        val outline = Paint(ghostBgPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            color = when (zone) {
                Zone.DELETE -> DELETE_RGB
                Zone.FAVORITE -> FAVORITE_RGB
                null -> Color.LTGRAY
            }
            setShadowLayer(0f, 0f, 0f, 0)
        }
        canvas.drawRoundRect(l, t, l + w, t + h, radius, radius, outline)

        val fm = ghostTextPaint.fontMetrics
        val baseline = t + h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(word, gx, baseline, ghostTextPaint)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )

    private companion object {
        const val STATUS_HIDDEN = 0
        const val STATUS_DRAG = 1
        const val STATUS_RETURN = 2
        const val STATUS_BURST = 3

        val DELETE_RGB = Color.rgb(255, 82, 82)
        val FAVORITE_RGB = Color.rgb(255, 199, 71)
    }
}