package com.romannepali.keyboard.emoji

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.R

/**
 * Slim horizontal emoji strip shown above the keys when the user switches
 * to the Emoji feature.  Tap an emoji to commit it directly.
 */
class EmojiPane @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(2), dp(6), dp(2))
    }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(container)
    }

    /** @param onEmoji called with the selected emoji character. */
    fun bind(dark: Boolean, onEmoji: (String) -> Unit) {
        container.removeAllViews()
        val mostUsed = listOf(
            "\uD83D\uDE0A", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83D\uDE14",
            "\uD83D\uDE05", "\uD83D\uDE2D", "\uD83D\uDE22", "\uD83D\uDE31",
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83D\uDC95",
            "\uD83D\uDCAF", "\uD83D\uDC4F", "\uD83D\uDCAA", "\uD83D\uDE4F",
            "\uD83C\uDF89", "\uD83C\uDFB5", "\uD83C\uDFA8", "\uD83D\uDE80",
            "\uD83D\uDCAC", "\u2705", "\u274C", "\u2764",
            "\uD83D\uDC40", "\uD83C\uDF1F", "\uD83C\uDF08", "\uD83C\uDF3B"
        )

        val emojiSize = resources.getDimensionPixelSize(R.dimen.emoji_pane_size).coerceAtLeast(dp(26))
        mostUsed.forEach { emoji ->
            val tv = TextView(context).apply {
                text = emoji
                textSize = emojiSize / resources.displayMetrics.density
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { onEmoji(emoji) }
            }
            container.addView(tv)
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}
