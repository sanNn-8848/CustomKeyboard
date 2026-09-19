package com.romannepali.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.settings.SettingsActivity

/**
 * Prediction chips with tap-to-insert and long-press-to-edit. Long-pressing a chip
 * hands the gesture to a [SuggestionDragOverlay] ("Suggestion Edit Mode"): the chip is
 * grabbed by the finger and can be dropped on a magnetic REMOVE / FAVORITE zone.
 * All gesture coordinates are RAW screen coords — the IME converts them to overlay space.
 */
class SuggestionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var chipsContainer: LinearLayout? = null
    private var undoButton: ImageButton? = null
    private var centerUndoButton: ImageButton? = null
    private var gearButton: ImageButton? = null
    private var chipsScroll: HorizontalScrollView? = null

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onUndoClickListener: (() -> Unit)? = null

    /** Dragged chip + the word and the raw screen coords where the touch started. */
    var onSuggestionDragStart: ((View, String, Float, Float) -> Unit)? = null
    var onSuggestionDragMove: ((Float, Float) -> Unit)? = null
    var onSuggestionDragEnd: ((Float, Float) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var pendingChip: View? = null
    private var pendingWord = ""
    private var lastChip: View? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragActive = false
    private var lastSuggestions: List<String> = emptyList()

    private val longPressRunnable = Runnable {
        val chip = pendingChip ?: return@Runnable
        dragActive = true
        lastChip = chip
        chip.visibility = View.INVISIBLE
        // Keep the suggestion scroller from hijacking the gesture once we start
        // dragging toward a zone, so the chip follows the finger across the
        // entire keyboard area instead of cancelling.
        chipsScroll?.requestDisallowInterceptTouchEvent(true)
        chip.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        onSuggestionDragStart?.invoke(chip, pendingWord, downRawX, downRawY)
    }

    init {
        inflate(context, R.layout.suggestion_bar, this)
        chipsContainer = findViewById(R.id.suggestion_chips_container)
        chipsScroll = findViewById(R.id.suggestion_scroll)
        undoButton = findViewById(R.id.clock_tap_undo)
        centerUndoButton = findViewById(R.id.undo_center)
        gearButton = findViewById(R.id.settings_gear)

        gearButton?.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        undoButton?.setOnClickListener {
            onUndoClickListener?.invoke()
        }
        centerUndoButton?.setOnClickListener {
            onUndoClickListener?.invoke()
        }
    }

    /**
     * Restores the chip that was grabbed once the drag overlay has finished playing
     * (after the drop burst or the fly-back animation) so nothing is left invisible.
     */
    fun restoreChip() {
        lastChip?.let {
            it.visibility = View.VISIBLE
            it.alpha = 1f
        }
        lastChip = null
    }

    private fun movedBeyondSlop(rawX: Float, rawY: Float): Boolean {
        val dx = rawX - downRawX
        val dy = rawY - downRawY
        return dx * dx + dy * dy > touchSlop * touchSlop
    }

    private fun onChipTouch(view: View, word: String, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pendingChip = view
                pendingWord = word
                downRawX = event.rawX
                downRawY = event.rawY
                dragActive = false
                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.postDelayed(longPressRunnable, longPressTimeout)
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragActive) {
                    chipsScroll?.requestDisallowInterceptTouchEvent(true)
                    onSuggestionDragMove?.invoke(event.rawX, event.rawY)
                } else if (movedBeyondSlop(event.rawX, event.rawY)) {
                    mainHandler.removeCallbacks(longPressRunnable)
                }
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                chipsScroll?.requestDisallowInterceptTouchEvent(false)
                if (dragActive) {
                    dragActive = false
                    pendingChip = null
                    onSuggestionDragEnd?.invoke(event.rawX, event.rawY)
                } else if (!movedBeyondSlop(event.rawX, event.rawY)) {
                    onSuggestionClickListener?.invoke(word)
                }
                pendingWord = ""
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                chipsScroll?.requestDisallowInterceptTouchEvent(false)
                if (dragActive) {
                    dragActive = false
                    pendingChip = null
                    onSuggestionDragEnd?.invoke(event.rawX, event.rawY)
                }
                pendingWord = ""
            }
        }
        return true
    }

    fun showSuggestions(suggestions: List<String>) {
        if (suggestions == lastSuggestions) return
        lastSuggestions = suggestions
        chipsContainer?.let { container ->
            container.removeAllViews()
            suggestions.forEachIndexed { index, text ->
                val chip = TextView(context).apply {
                    this.text = text
                    setTextColor(resources.getColor(R.color.letter_text_dark, null))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    background = resources.getDrawable(
                        if (index == 0) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_dark,
                        null
                    )
                    typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(36)
                    ).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    }
                    val chipText = text.toString()
                    setOnTouchListener { view, event -> onChipTouch(view, chipText, event) }
                    container.addView(this, lp)
                }
            }
            chipsScroll?.scrollTo(0, 0)
            // Chips spring in from below with a quick stagger.
            container.post {
                for (i in 0 until container.childCount) {
                    val chip = container.getChildAt(i)
                    chip.pivotX = chip.width / 2f
                    chip.pivotY = chip.height / 2f
                    chip.alpha = 0f
                    chip.scaleX = 0.7f
                    chip.scaleY = 0.7f
                    chip.translationY = dp(8).toFloat()
                    chip.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setStartDelay(i * 24L)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                }
            }
        }
    }

    fun clearSuggestions() {
        chipsContainer?.removeAllViews()
        lastSuggestions = emptyList()
        mainHandler.removeCallbacks(longPressRunnable)
        pendingChip = null
        restoreChip()
    }

    fun setUndoState(available: Boolean, centered: Boolean) {
        if (centered) {
            undoButton?.visibility = GONE
            gearButton?.visibility = GONE
            chipsContainer?.visibility = GONE
            chipsScroll?.visibility = GONE
            centerUndoButton?.visibility = VISIBLE
        } else {
            centerUndoButton?.visibility = GONE
            gearButton?.visibility = VISIBLE
            undoButton?.visibility = if (available) VISIBLE else GONE
            chipsContainer?.visibility = VISIBLE
            chipsScroll?.visibility = VISIBLE
        }
    }

    @Deprecated("Use setUndoState instead", replaceWith = ReplaceWith("setUndoState(visible, false)"))
    fun setUndoVisible(visible: Boolean) = setUndoState(visible, false)

    /** FrostGlass theme switch for the prediction chips and bar icons. */
    fun applyTheme(dark: Boolean) {
        val chip = if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light
        val selected = if (dark) R.drawable.bg_suggestion_chip_dark_selected else R.drawable.bg_suggestion_chip_light_selected
        val textRes = if (dark) R.color.letter_text_dark else R.color.letter_text_light
        val icon = resources.getColor(if (dark) R.color.icon_dark else R.color.icon_light, null)
        val tint = android.content.res.ColorStateList.valueOf(icon)

        chipsContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val view = container.getChildAt(i) as? TextView ?: continue
                view.setBackgroundResource(if (i == 0) selected else chip)
                view.setTextColor(resources.getColor(textRes, null))
            }
        }

        undoButton?.imageTintList = tint
        centerUndoButton?.imageTintList = tint
        gearButton?.imageTintList = tint
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}