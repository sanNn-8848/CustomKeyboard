package com.romannepali.keyboard.emoji

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.R
import org.json.JSONObject

/**
 * Slim horizontal emoji strip shown above the keys when the user switches
 * to the Emoji feature.  Tap an emoji to commit it directly.
 *
 * Emoji usage is tracked in prefs: the emoji you use the most always rise to
 * the front of the strip (positions 1-4), so your favourites stay one tap away.
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

    private val prefs =
        context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)

    private fun loadUsage(): JSONObject {
        val raw = prefs.getString("emoji_usage", "{}") ?: "{}"
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun recordUsage(emoji: String) {
        try {
            val usage = loadUsage()
            val next = usage.optInt(emoji, 0) + 1
            usage.put(emoji, next)
            // Never let the map grow unbounded.
            while (usage.length() > 120) {
                val minKey = usage.keys().asSequence()
                    .minByOrNull { usage.optInt(it, 0) }
                    ?: break
                usage.remove(minKey)
            }
            prefs.edit().putString("emoji_usage", usage.toString()).apply()
        } catch (_: Exception) {
            // Ignore malformed usage data.
        }
    }

    init {
        isHorizontalScrollBarEnabled = false
        isSmoothScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        addView(container)
    }

    /** @param onEmoji called with the selected emoji character. */
    fun bind(dark: Boolean, onEmoji: (String) -> Unit) {
        val usage = loadUsage()
        // Most-used first (positions 1-4 = your top emoji), then defaults.
        val ordered = LinkedHashSet<String>()
        usage.keys()
            .asSequence()
            .map { it to usage.optInt(it, 0) }
            .sortedByDescending { it.second }
            .forEach { (emoji, _) -> ordered.add(emoji) }
        DEFAULT_EMOJIS.forEach { ordered.add(it) }
        val visible = ordered.take(MAX_EMOJIS).toList()

        container.removeAllViews()
        val emojiSize = resources.getDimensionPixelSize(R.dimen.emoji_pane_size).coerceAtLeast(dp(26))
        visible.forEachIndexed { index, emoji ->
            val tv = TextView(context).apply {
                text = emoji
                textSize = emojiSize / resources.displayMetrics.density
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener {
                    recordUsage(emoji)
                    onEmoji(emoji)
                }
            }
            container.addView(tv)
        }

        // Soft pop-in, staggered left to right, and a gentle glide back to 0.
        container.post {
            for (i in 0 until container.childCount) {
                val chip = container.getChildAt(i)
                chip.pivotX = chip.width / 2f
                chip.pivotY = chip.height / 2f
                chip.alpha = 0f
                chip.scaleX = 0.75f
                chip.scaleY = 0.75f
                chip.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(i * 18L)
                    .setDuration(180)
                    .setInterpolator(OvershootInterpolator(2.0f))
                    .start()
            }
            smoothScrollTo(0, 0)
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private companion object {
        const val MAX_EMOJIS = 48
        val DEFAULT_EMOJIS = listOf(
            "\uD83D\uDE0A", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83D\uDE14",
            "\uD83D\uDE05", "\uD83D\uDE2D", "\uD83D\uDE22", "\uD83D\uDE31",
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83D\uDC95",
            "\uD83D\uDCAF", "\uD83D\uDC4F", "\uD83D\uDCAA", "\uD83D\uDE4F",
            "\uD83C\uDF89", "\uD83C\uDFB5", "\uD83C\uDFA8", "\uD83D\uDE80",
            "\uD83D\uDCAC", "\u2705", "\u274C", "\u2764",
            "\uD83D\uDC40", "\uD83C\uDF1F", "\uD83C\uDF08", "\uD83C\uDF3B",
            "\uD83D\uDC31", "\uD83D\uDC36", "\uD83E\uDD81", "\uD83C\uDF4B",
            "\uD83D\uDE48", "\uD83D\uDE49", "\uD83D\uDE4A", "\uD83E\uDD14",
            "\uD83D\uDE3A", "\uD83D\uDC8B", "\uD83E\uDD1E", "\uD83D\uDE44",
            "\uD83D\uDD25", "\uD83C\uDFC3", "\uD83E\uDD29", "\uD83D\uDEBE",
            "\uD83D\uDCA1", "\uD83C\uDF93", "\u26BD", "\uD83C\uDF40"
        )
    }
}