package com.romannepali.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Full-screen "Suggestion Edit Mode" overlay. When a suggestion chip is long-pressed the
 * keyboard dims and two floating, invisible magnetic zones appear (REMOVE / FAVORITE).
 * The dragged chip is drawn as a glowing ghost that follows the finger; the zones
 * continuously react to proximity (halo glow, warm-up color, scale and icon animation)
 * and snap the ghost slightly toward them. Releasing inside a zone performs the action
 * with a confirm burst; releasing anywhere else flies the ghost back to its origin.
 *
 * Coordinates passed in are overlay-local (0,0 = overlay top-left).
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

    private val zoneR = dp(34f)
    private val zoneActiveR = dp(41f)
    private val magnetR = dp(96f)
    private val glowR = dp(74f)
    private val zoneMarginX = dp(48f)
    private val zoneTop = dp(48f)

    private val removeCenter = PointF()
    private val favoriteCenter = PointF()

    private var active = false
    private var status = STATUS_HIDDEN

    private var word = ""
    private val origin = Rect()
    private var ghostX = 0f
    private var ghostY = 0f
    private var fingerX = 0f
    private var fingerY = 0f

    private var burstZone: Zone? = null
    private var burstProgress = 0f
    private var burstAnimator: ValueAnimator? = null

    private var returnAnimator: ValueAnimator? = null

    // Proximity / haptic bookkeeping (recomputed every motion event).
    private var inMagnet = false
    private var inDrop = false

    private enum class Zone { REMOVE, FAVORITE }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        removeCenter.set(zoneMarginX, zoneTop)
        favoriteCenter.set(w - zoneMarginX, zoneTop)
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun beginDrag(draggedWord: String, from: Rect, touchX: Float, touchY: Float) {
        if (active) cancelAnimations()
        word = draggedWord
        origin.set(from)
        fingerX = touchX
        fingerY = touchY
        ghostX = touchX + dp(1)
        ghostY = touchY + dp(1)
        inMagnet = false
        inDrop = false
        active = true
        status = STATUS_DRAG
        visibility = VISIBLE
        haptic(Haptics.GESTURE_START)
        invalidate()
    }

    fun updateFinger(x: Float, y: Float) {
        if (!active) return
        fingerX = x
        fingerY = y
        val zoneState = nearestZone(x, y)
        applyProximityHaptics(zoneState)
        invalidate()
    }

    fun finishDrag(x: Float, y: Float) {
        if (!active) return
        fingerX = x
        fingerY = y
        val nearest = nearestZone(x, y)
        when {
            nearest.drop -> performDrop(nearest.zone)
            else -> flyBack()
        }
    }

    fun cancelDrag() {
        if (!active) return
        flyBack()
    }

    /** Cleanly ends any in-flight drag so a new one can take over. */
    fun endCurrentDragIfAny() {
        if (isDragActive()) flyBack()
        if (status == STATUS_BURST || status == STATUS_RETURN) hide()
    }

    fun isDragActive(): Boolean = active && status == STATUS_DRAG

    // ------------------------------------------------------------------
    // Drop / return
    // ------------------------------------------------------------------

    private fun performDrop(zone: Zone) {
        active = false
        status = STATUS_BURST
        burstZone = zone
        burstProgress = 0f
        haptic(Haptics.CONFIRM)
        val targetCenter = if (zone == Zone.REMOVE) removeCenter else favoriteCenter
        burstAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            start()
            addUpdateListener {
                burstProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    when (zone) {
                        Zone.REMOVE -> onRemoveRequested?.invoke(word)
                        Zone.FAVORITE -> onFavoriteRequested?.invoke(word)
                    }
                    hide()
                }
            })
        }
        // Draw one frame of the burst centered on the zone.
        burstX = targetCenter.x
        burstY = targetCenter.y
    }

    private fun flyBack() {
        active = false
        status = STATUS_RETURN
        val startX = ghostX
        val startY = ghostY
        val endX = origin.centerX().toFloat()
        val endY = origin.centerY().toFloat()
        returnAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.3f)
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

    private fun hide() {
        burstZone = null
        status = STATUS_HIDDEN
        visibility = INVISIBLE
        onDragFinished?.invoke()
        invalidate()
    }

    private fun cancelAnimations() {
        burstAnimator?.cancel()
        returnAnimator?.cancel()
        burstAnimator = null
        returnAnimator = null
    }

    // ------------------------------------------------------------------
    // Proximity + haptics
    // ------------------------------------------------------------------

    private data class ZoneHit(val zone: Zone, val drop: Boolean)

    private fun nearestZone(x: Float, y: Float): ZoneHit {
        val removeD = dist(x, y, removeCenter)
        val favD = dist(x, y, favoriteCenter)
        return if (removeD <= favD) {
            ZoneHit(Zone.REMOVE, removeD <= activeDropRadius())
        } else {
            ZoneHit(Zone.FAVORITE, favD <= activeDropRadius())
        }
    }

    private fun applyProximityHaptics(hit: ZoneHit) {
        val magnetRadius = magnetR
        val dropRadius = activeDropRadius()
        val nearestD = if (hit.zone == Zone.REMOVE) dist(fingerX, fingerY, removeCenter)
        else dist(fingerX, fingerY, favoriteCenter)

        val nowMagnet = nearestD <= magnetRadius
        val nowDrop = nearestD <= dropRadius
        if (nowMagnet && !inMagnet) haptic(Haptics.MAGNET_ENTER)
        if (!nowMagnet && inMagnet) haptic(Haptics.MAGNET_LEAVE)
        if (nowDrop && !inDrop) haptic(Haptics.DROP_ACHIEVED)
        if (!nowDrop && inDrop) haptic(Haptics.DROP_LEFT)
        inMagnet = nowMagnet
        inDrop = nowDrop
    }

    private fun activeDropRadius(): Float = zoneActiveR

    private enum class Haptics {
        GESTURE_START, MAGNET_ENTER, MAGNET_LEAVE, DROP_ACHIEVED, DROP_LEFT, CONFIRM
    }

    private fun haptic(kind: Haptics) {
        val constant = when (kind) {
            Haptics.GESTURE_START -> HapticFeedbackConstants.GESTURE_START
            Haptics.MAGNET_ENTER -> HapticFeedbackConstants.GESTURE_START
            Haptics.MAGNET_LEAVE -> HapticFeedbackConstants.KEYBOARD_TAP
            Haptics.DROP_ACHIEVED -> HapticFeedbackConstants.GESTURE_END
            Haptics.DROP_LEFT -> HapticFeedbackConstants.KEYBOARD_TAP
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

    private val scrimPaint = Paint().apply { color = Color.argb(175, 4, 7, 12) }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        textSize = dp12()
        textAlign = Paint.Align.CENTER
    }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(26).toFloat()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = dp(10).toFloat()
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

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val phase = SystemClock.uptimeMillis() / 1000f * 2.5f

        // Zones (hidden until the user starts dragging).
        if (status == STATUS_DRAG || status == STATUS_RETURN || status == STATUS_BURST) {
            drawZone(canvas, removeCenter, Zone.REMOVE, phase)
            drawZone(canvas, favoriteCenter, Zone.FAVORITE, phase)
        }

        // Flying ghost.
        if (status == STATUS_DRAG || status == STATUS_RETURN) {
            drawGhost(canvas, phase)
        }

        // Drop burst flash.
        if (status == STATUS_BURST && burstZone != null) {
            val t = burstProgress
            val color = if (burstZone == Zone.REMOVE) REMOVE_RGB else FAVORITE_RGB
            burstPaint.color = Color.argb(((1f - t) * 230).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            val radius = dp(20) + t * dp(64)
            canvas.drawCircle(burstX, burstY, radius, burstPaint)
            canvas.drawCircle(burstX, burstY, radius * 0.6f, burstPaint)
        }

        hintPaint.alpha = 150
        canvas.drawText(
            context.getString(R.string.suggestion_drag_hint),
            width / 2f,
            height - dp(16).toFloat(),
            hintPaint
        )
    }

    private fun drawZone(canvas: Canvas, center: PointF, zone: Zone, phase: Float) {
        val cx = center.x
        val cy = center.y
        val d = dist(fingerX, fingerY, center)
        val intensity = (1f - min(1f, d / glowR)).coerceIn(0f, 1f)
        val isDrop = d <= activeDropRadius()
        val rgb = if (zone == Zone.REMOVE) REMOVE_RGB else FAVORITE_RGB

        val ease = intensity * intensity * (3f - 2f * intensity)
        val discR = lerp(zoneR, zoneActiveR, if (isDrop) ease.toFloat() else ease.toFloat())
        val shine = if (isDrop) 0.95f else ease

        // Soft magnetic halo (blurred energy field, stronger as the chip approaches).
        if (shine > 0.02f) {
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val inner = Color.argb((190 * shine).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            val mid = Color.argb((110 * shine).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            val core = Color.argb((70 * shine).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            haloPaint.shader = RadialGradient(
                cx, cy,
                glowR,
                intArrayOf(inner, mid, Color.TRANSPARENT),
                floatArrayOf(0.12f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, glowR, haloPaint)
        }

        // Zone disc — warms up and expands on approach.
        discPaint.color = Color.argb(
            (72 + 168 * shine).toInt(),
            Color.red(rgb), Color.green(rgb), Color.blue(rgb)
        )
        canvas.drawCircle(cx, cy, discR, discPaint)

        // Inner energy ring that brightens inside the drop zone.
        if (shine > 0.1f) {
            ringPaint.color = Color.argb((80 + 150 * shine).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
            canvas.drawCircle(cx, cy, discR - dp(4), ringPaint)
            // Slow idling scan ring that "breathes".
            ringPaint.alpha = (60 * (0.5f + 0.5f * sin(phase))).toInt()
            canvas.drawCircle(cx, cy, discR + dp(7) + 2f * sin(phase), ringPaint)
        }

        // Icon with zone-specific animation.
        val iconCenterY = cy - dp(9)
        val iconCenterX = cx
        if (zone == Zone.REMOVE) {
            canvas.save()
            val tilt = 14f * intensity
            canvas.rotate(-tilt, iconCenterX, iconCenterY)
            drawEmoji(canvas, "\uD83D\uDDD1\uFE0F", iconCenterX, iconCenterY)
            canvas.restore()
        } else {
            val pulse = 1f + 0.18f * intensity * (0.55f + 0.45f * sin(phase * 1.6f))
            canvas.save()
            canvas.scale(pulse, pulse, iconCenterX, iconCenterY)
            drawEmoji(canvas, "\u2B50", iconCenterX, iconCenterY)
            canvas.restore()
        }

        // Label.
        labelPaint.alpha = (90 + 150 * shine).toInt()
        canvas.drawText(
            if (zone == Zone.REMOVE) context.getString(R.string.suggestion_remove_zone)
            else context.getString(R.string.suggestion_favorite_zone),
            cx,
            cy + zoneActiveR + dp(14),
            labelPaint
        )
    }

    private fun drawEmoji(canvas: Canvas, icon: String, cx: Float, cy: Float) {
        iconPaint.alpha = 255
        val fm = iconPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(icon, cx, baseline, iconPaint)
    }

    private fun drawGhost(canvas: Canvas, phase: Float) {
        // Magnetic pull: nudge the ghost toward the nearest zone inside its field.
        var gx = ghostX
        var gy = ghostY
        val nearest = nearestZone(fingerX, fingerY)
        val center = if (nearest.zone == Zone.REMOVE) removeCenter else favoriteCenter
        val d = dist(fingerX, fingerY, center)
        if (d < magnetR && status == STATUS_DRAG) {
            val pull = (1f - d / magnetR).coerceIn(0f, 1f)
            val factor = pull * pull * 0.24f
            gx += (center.x - gx) * factor
            gy += (center.y - gy) * factor
        }

        val textW = ghostTextPaint.measureText(word)
        val w = max(dp(64).toFloat(), textW + dp(40).toFloat())
        val h = dp(42).toFloat()
        val l = gx - w / 2f
        val t = gy - h / 2f
        val radius = dp(12).toFloat()

        ghostBgPaint.setShadowLayer(dp(10).toFloat(), 0f, dp(6).toFloat(), Color.argb(110, 0, 0, 0))
        canvas.drawRoundRect(l, t, l + w, t + h, radius, radius, ghostBgPaint)

        // Thin accent outline that follows the proximity heat.
        val nearestTint = if (nearest.zone == Zone.REMOVE) REMOVE_RGB else FAVORITE_RGB
        val outline = Paint(ghostBgPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f)
            color = if (d < magnetR) nearestTint else Color.LTGRAY
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

    private fun dist(x: Float, y: Float, p: PointF): Float =
        kotlin.math.sqrt((x - p.x) * (x - p.x) + (y - p.y) * (y - p.y))

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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

    /** Smaller helper for the tiny hint/icon text so call sites stay readable. */
    private fun dp12(): Float = dp(12f)

    private companion object {
        const val STATUS_HIDDEN = 0
        const val STATUS_DRAG = 1
        const val STATUS_RETURN = 2
        const val STATUS_BURST = 3

        val REMOVE_RGB = Color.rgb(255, 82, 82)
        val FAVORITE_RGB = Color.rgb(255, 199, 71)
    }
}