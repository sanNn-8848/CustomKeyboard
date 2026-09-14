package com.romannepali.keyboard

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
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

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onUndoClickListener: (() -> Unit)? = null

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
                    container.addView(this, lp)
                }
            }
            chipsScroll?.scrollTo(0, 0)
        }
    }

    fun clearSuggestions() {
        chipsContainer?.removeAllViews()
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
