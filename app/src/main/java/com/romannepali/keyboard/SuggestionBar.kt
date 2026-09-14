package com.romannepali.keyboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.settings.SettingsActivity

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
    private var deleteOverlay: FrameLayout? = null
    private var deleteGlow: ImageView? = null
    private var blinkAnimator: ObjectAnimator? = null

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onDeleteSuggestionClickListener: ((String) -> Unit)? = null
    var onUndoClickListener: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        inflate(context, R.layout.suggestion_bar, this)
        chipsContainer = findViewById(R.id.suggestion_chips_container)
        chipsScroll = findViewById(R.id.suggestion_scroll)
        undoButton = findViewById(R.id.clock_tap_undo)
        centerUndoButton = findViewById(R.id.undo_center)
        gearButton = findViewById(R.id.settings_gear)

        buildDeleteOverlay()

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

    private fun buildDeleteOverlay() {
        val overlay = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = resources.getDrawable(R.drawable.bg_delete_glow, null)
            setPadding(dp(14), dp(6), dp(18), dp(6))
        }

        val glow = ImageView(context).apply {
            background = resources.getDrawable(R.drawable.bg_delete_glow, null)
            contentDescription = null
            visibility = GONE
            layoutParams = FrameLayout.LayoutParams(
                dp(44),
                dp(44),
                Gravity.CENTER
            )
        }
        overlay.addView(glow)

        val trash = ImageButton(context).apply {
            background = null
            setImageResource(R.drawable.ic_trash)
            imageTintList = ColorStateList.valueOf(resources.getColor(R.color.accent, null))
            contentDescription = resources.getString(R.string.delete_suggestion)
            val counterLp = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
            layoutParams = counterLp
            setOnClickListener {
                val word = tag as? String
                stopDeleteBlink()
                overlay.visibility = GONE
                if (word != null) {
                    onDeleteSuggestionClickListener?.invoke(word)
                }
            }
        }
        overlay.addView(trash)

        val label = TextView(context).apply {
            text = resources.getString(R.string.delete_suggestion)
            setTextColor(resources.getColor(R.color.accent_soft, null))
            textSize = 13f
            isSingleLine = true
            setPadding(dp(6), 0, 0, 0)
        }
        pill.addView(label)
        overlay.addView(pill)

        overlay.visibility = GONE
        addView(overlay)
        deleteOverlay = overlay
        deleteGlow = glow
    }

    private fun showDeleteOverlay(word: String) {
        val overlay = deleteOverlay ?: return
        overlay.tag = word
        overlay.visibility = VISIBLE
        startDeleteBlink()
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            overlay.visibility = GONE
            stopDeleteBlink()
        }, 3000)
    }

    private fun startDeleteBlink() {
        val glow = deleteGlow ?: return
        stopDeleteBlink()
        glow.visibility = VISIBLE
        blinkAnimator = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.35f, 1f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopDeleteBlink() {
        blinkAnimator?.cancel()
        blinkAnimator = null
        deleteGlow?.let {
            it.visibility = GONE
            it.alpha = 1f
        }
    }

    fun showSuggestions(suggestions: List<String>) {
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
                    setOnClickListener { onSuggestionClickListener?.invoke(text.toString()) }
                    setOnLongClickListener {
                        showDeleteOverlay(text.toString())
                        true
                    }
                    container.addView(this, lp)
                }
            }
            chipsScroll?.scrollTo(0, 0)
        }
        deleteOverlay?.let { overlay ->
            overlay.visibility = GONE
            stopDeleteBlink()
        }
    }

    fun clearSuggestions() {
        chipsContainer?.removeAllViews()
        deleteOverlay?.let { overlay ->
            overlay.visibility = GONE
            stopDeleteBlink()
        }
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
        val tint = ColorStateList.valueOf(icon)

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