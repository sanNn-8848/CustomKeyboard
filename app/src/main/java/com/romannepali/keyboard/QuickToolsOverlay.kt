package com.romannepali.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.round

/** Compact edge overlay. Existing keyboard rows/weights/Space are untouched. */
class QuickToolsOverlay(
    context: Context,
    private val inputConnectionProvider: () -> android.view.inputmethod.InputConnection?
) : FrameLayout(context) {

    private val rail = dp(24)
    private val wheel = CylinderWheelView(context)

    init {
        clipChildren = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        addView(
            rail("▣") { toggleEdit() },
            FrameLayout.LayoutParams(
                rail,
                dp(72),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )

        addView(
            rail("☺") { toggleEmoji() },
            FrameLayout.LayoutParams(
                rail,
                dp(72),
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        )

        wheel.setItems(
            listOf(
                "Copy",
                "Paste",
                "Cut",
                "Select all",
                "Undo",
                "Redo"
            )
        )

        wheel.onItemClick = { performEdit(it) }

        wheel.visibility = GONE

        addView(
            wheel,
            FrameLayout.LayoutParams(
                dp(148),
                dp(178),
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                leftMargin = rail + dp(2)
            }
        )
    }

    private fun rail(
        label: String,
        click: () -> Unit
    ) = TextView(context).apply {

        text = label
        textSize = 13f
        gravity = Gravity.CENTER

        setTextColor(android.graphics.Color.WHITE)

        setBackgroundColor(
            android.graphics.Color.argb(
                145,
                55,
                55,
                62
            )
        )

        setOnClickListener {
            click()
        }
    }

    private fun toggleEdit() {
        wheel.visibility =
            if (wheel.visibility == VISIBLE) {
                GONE
            } else {
                VISIBLE
            }
    }

    private fun toggleEmoji() {
        wheel.visibility = GONE

        val panel = TextView(context).apply {

            text =
                "😀  😂  😍  🥰  😎\n" +
                "😭  🙏  ❤️  🔥  👍\n" +
                "🎉  😊  🤔  😅  ✨"

            textSize = 24f
            gravity = Gravity.CENTER

            setTextColor(android.graphics.Color.WHITE)

            setBackgroundColor(
                android.graphics.Color.argb(
                    235,
                    35,
                    35,
                    42
                )
            )

            setOnClickListener {
                // Emoji selection will be connected in the next pass.
            }
        }

        addView(
            panel,
            FrameLayout.LayoutParams(
                dp(190),
                dp(150),
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                rightMargin = rail + dp(2)
            }
        )

        panel.postDelayed(
            {
                if (panel.parent === this) {
                    removeView(panel)
                }
            },
            3500L
        )
    }

    private fun performEdit(action: String) {

        val ic = inputConnectionProvider()
            ?: return

        val id = when (action) {

            "Copy" ->
                android.R.id.copy

            "Paste" ->
                android.R.id.paste

            "Cut" ->
                android.R.id.cut

            "Select all" ->
                android.R.id.selectAll

            "Undo" ->
                android.R.id.undo

            "Redo" ->
                android.R.id.redo

            else ->
                return
        }

        ic.performContextMenuAction(id)

        wheel.visibility = GONE
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density + .5f).toInt()
}


/**
 * Lightweight perspective/cylinder wheel:
 * continuous drag + short snap,
 * no permanent animation loop.
 */
class CylinderWheelView(
    context: Context
) : View(context) {

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private var items =
        emptyList<String>()

    private var center = 0f

    private var downY = 0f
    private var downCenter = 0f

    private var velocity = 0f

    private var lastY = 0f
    private var lastTime = 0L

    private var snapStart = 0L
    private var snapFrom = 0f
    private var snapTo = 0f

    var onItemClick:
        ((String) -> Unit)? = null

    fun setItems(
        values: List<String>
    ) {
        items = values

        center =
            center.coerceIn(
                0f,
                max(
                    0,
                    items.lastIndex
                ).toFloat()
            )

        invalidate()
    }

    override fun onDraw(
        c: Canvas
    ) {

        super.onDraw(c)

        if (items.isEmpty()) {
            return
        }

        val mid =
            height / 2f

        val radius =
            max(
                42f,
                height * .34f
            )

        for (i in items.indices) {

            val d =
                i - center

            if (abs(d) > 2.35f) {
                continue
            }

            val angle =
                (d * .62f)
                    .coerceIn(
                        -1.45f,
                        1.45f
                    )

            val y =
                mid +
                    sin(angle) * radius

            val depth =
                cos(angle)
                    .coerceIn(
                        0f,
                        1f
                    )

            paint.textSize =
                13f *
                    (
                        .62f +
                            .38f * depth
                        )

            paint.alpha =
                (
                    70 +
                        185 * depth
                    ).toInt()

            paint.color =
                android.graphics.Color.WHITE

            paint.typeface =
                android.graphics.Typeface.DEFAULT_BOLD

            val text =
                items[i]

            c.drawText(
                text,
                width / 2f -
                    paint.measureText(text) / 2f,
                y +
                    paint.textSize / 3f,
                paint
            )
        }

        paint.alpha = 255

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth = 1.5f

        paint.color =
            android.graphics.Color.argb(
                90,
                255,
                255,
                255
            )

        c.drawRoundRect(
            RectF(
                dp(8).toFloat(),
                mid - dp(19),
                width -
                    dp(8).toFloat(),
                mid + dp(19)
            ),
            dp(12).toFloat(),
            dp(12).toFloat(),
            paint
        )

        paint.style =
            Paint.Style.FILL
    }

    override fun onTouchEvent(
        e: MotionEvent
    ): Boolean {

        when (e.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                downY = e.y
                lastY = e.y

                downCenter = center

                lastTime =
                    SystemClock.uptimeMillis()

                velocity = 0f

                parent
                    ?.requestDisallowInterceptTouchEvent(
                        true
                    )

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                val now =
                    SystemClock.uptimeMillis()

                val dt =
                    max(
                        1L,
                        now - lastTime
                    )

                val dy =
                    e.y - lastY

                center =
                    (
                        downCenter -
                            (
                                e.y - downY
                            ) /
                            dp(42).toFloat()
                    ).coerceIn(
                        0f,
                        items.lastIndex.toFloat()
                    )

                velocity =
                    -dy / dt.toFloat()

                lastY = e.y
                lastTime = now

                invalidate()

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                parent
                    ?.requestDisallowInterceptTouchEvent(
                        false
                    )

                snapFrom = center

                snapTo =
                    (
                        center +
                            if (abs(velocity) > .7f) {
                                velocity * 2f
                            } else {
                                0f
                            }
                    )
                        .coerceIn(
                            0f,
                            items.lastIndex.toFloat()
                        )
                        .roundToInt()
                        .toFloat()

                snapStart =
                    SystemClock.uptimeMillis()

                postOnAnimation(
                    object : Runnable {

                        override fun run() {

                            val t =
                                (
                                    (
                                        SystemClock.uptimeMillis() -
                                            snapStart
                                    ) / 150f
                                ).coerceIn(
                                    0f,
                                    1f
                                )

                            val eased =
                                1f -
                                    (
                                        1f - t
                                    ) *
                                    (
                                        1f - t
                                    )

                            center =
                                snapFrom +
                                    (
                                        snapTo -
                                            snapFrom
                                    ) * eased

                            invalidate()

                            if (t < 1f) {

                                postOnAnimation(
                                    this
                                )

                            } else {

                                onItemClick?.invoke(
                                    items[
                                        center.roundToInt()
                                    ]
                                )
                            }
                        }
                    }
                )

                return true
            }
        }

        return true
    }

    private fun dp(v: Int): Float =
        v *
            resources
                .displayMetrics
                .density

    private fun Float.roundToInt(): Int =
        round(this).toInt()
}

