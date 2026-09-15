package com.romannepali.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.roundToInt

data class FeatureItem(val label: String, val icon: String)

class FeatureCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var items: List<FeatureItem> = emptyList()
        set(value) {
            field = value
            selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            invalidate()
        }

    var selectedIndex: Int = 0
        private set

    var onItemSelected: ((Int) -> Unit)? = null

    // Smooth offset drives the animation; snaps to selectedIndex on release.
    private var offset = 0f

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 24f * resources.displayMetrics.density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.density
        typeface = Typeface.DEFAULT_BOLD
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f * resources.displayMetrics.density
    }
    private val dimLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * resources.displayMetrics.density
    }

    private val density = resources.displayMetrics.density
    private val itemSpacing get() = width / 3f

    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var lastX = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var lastTickIndex = -1
    private var snapAnimator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas) {
        if (items.isEmpty()) return
        val cx = width / 2f
        val cy = height / 2f
        val spacing = itemSpacing

        // Draw items from farthest to nearest so center overlaps edges.
        for (raw in -2..2) {
            val d = raw.toFloat()
            val itemIndex = (offset + d).roundToInt()
            if (itemIndex < 0 || itemIndex >= items.size) continue

            val item = items[itemIndex]
            val absDist = abs(d)
            val scale = when {
                absDist < 0.01f -> 1.0f
                absDist < 0.5f  -> 0.88f
                absDist < 1.0f  -> 0.78f
                else            -> 0.60f
            }
            val alpha = when {
                absDist < 0.01f -> 1.0f
                absDist < 0.5f  -> 0.85f
                absDist < 1.0f  -> 0.65f
                else            -> 0.35f
            }

            val x = cx + d * spacing
            // Depth curve: farther items sink a few px.
            val yOffset = absDist * 4f * density

            val isCenter = absDist < 0.01f

            canvas.save()

            if (isCenter) {
                iconPaint.alpha = (alpha * 255).toInt()
                labelPaint.alpha = (alpha * 255).toInt()
                canvas.drawText(item.icon, x, cy - 4 * density, iconPaint)
                canvas.drawText(item.label, x, cy + 14 * density, labelPaint)
            } else {
                dimPaint.alpha = (alpha * 255).toInt()
                dimLabelPaint.alpha = (alpha * 255).toInt()
                canvas.drawText(item.icon, x, cy + yOffset - 2 * density, dimPaint)
                canvas.drawText(item.label, x, cy + yOffset + 10 * density, dimLabelPaint)
            }

            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                downX = event.x
                lastX = event.x
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastX
                if (abs(event.x - downX) > touchSlop) dragging = true
                if (dragging) {
                    offset -= dx / itemSpacing
                    offset = offset.coerceIn(0f, (items.size - 1).toFloat())
                    lastX = event.x
                    detectTick()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                if (dragging) {
                    val vx = velocityTracker?.xVelocity ?: 0f
                    // Momentum: fling snaps past center if fast enough.
                    val momentum = if (abs(vx) > 800f) (-vx / itemSpacing * 0.15f).roundToInt() else 0
                    val target = (offset + 0.5f).roundToInt().coerceIn(0, items.size - 1) + momentum
                    snapTo(target.coerceIn(0, items.size - 1))
                } else {
                    // Tap: find which item was closest.
                    val tapped = (offset + (event.x - width / 2f) / itemSpacing).roundToInt()
                        .coerceIn(0, items.size - 1)
                    snapTo(tapped)
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectTick() {
        val tick = offset.roundToInt().coerceIn(0, items.size - 1)
        if (tick != lastTickIndex) {
            lastTickIndex = tick
            performHapticFeedback(
                if (tick == selectedIndex) android.view.HapticFeedbackConstants.KEYBOARD_TAP
                else android.view.HapticFeedbackConstants.CLOCK_TICK
            )
        }
    }

    private fun snapTo(index: Int) {
        val target = index.coerceIn(0, items.size - 1)
        val start = offset
        val end = target.toFloat()
        if (abs(start - end) < 0.01f) {
            offset = end
            finishSnap(target)
            return
        }
        snapAnimator = ValueAnimator.ofFloat(start, end).apply {
            duration = 220
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener { offset = it.animatedValue as Float; invalidate() }
            doOnEnd { finishSnap(target) }
            start()
        }
    }

    private fun finishSnap(index: Int) {
        offset = index.toFloat()
        if (index != selectedIndex) {
            selectedIndex = index
            // Stronger tick on final selection.
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            onItemSelected?.invoke(index)
        }
        invalidate()
    }

    // Extension to handle Animator.doOnEnd cleanly.
    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }
}
