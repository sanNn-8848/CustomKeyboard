package com.romannepali.keyboard.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.R

/**
 * Slim horizontal pane that shows the most recent clipboard clips.
 * Tap a chip → its text is pasted into the editor.
 */
class ClipboardPane @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private val emptyLabel = TextView(context).apply {
        text = "Nothing copied yet."
        textSize = 13f
        setTextColor(resources.getColor(R.color.icon_light, null))
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(container)
    }

    /** @param onPaste called with the clip text when the user taps a chip. */
    fun bind(manager: ClipboardManager, dark: Boolean, onPaste: (String) -> Unit) {
        container.removeAllViews()
        val items = manager.getHistory().take(20)
        if (items.isEmpty()) {
            container.addView(emptyLabel)
            return
        }

        val textRes = if (dark) R.color.letter_text_dark else R.color.letter_text_light
        val chipBg = if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light

        items.forEach { clip ->
            val display = clip.text.replace("\n", " ").trim()
            val chip = TextView(context).apply {
                text = if (display.length > 40) display.take(40) + "\u2026" else display
                setTextColor(resources.getColor(textRes, null))
                textSize = 13f
                gravity = Gravity.CENTER
                isSingleLine = true
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = resources.getDrawable(chipBg, null)
                setOnClickListener { onPaste(clip.text) }
            }
            container.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
            ).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
