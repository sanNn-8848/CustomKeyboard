package com.romannepali.keyboard.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.R

/** A single tappable action rendered inside the [GlassActionPanel]. */
data class ActionItem(
    val id: Int,
    val iconRes: Int,
    val label: String,
    val enabled: Boolean
)

/**
 * Floating frosted-glass panel with static, directly-tappable action buttons
 * (Select all / Copy / Paste / Cut) laid out side by side. Open/close animation,
 * theme-aware glass background, dimmed disabled actions.
 */
class GlassActionPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var dark = false
    private var dismissing = false

    fun setTheme(isDark: Boolean) {
        dark = isDark
        background = resources.getDrawable(
            if (dark) R.drawable.bg_gesture_menu_dark else R.drawable.bg_gesture_menu_light,
            null
        )
    }

    /** Renders the given actions side by side; tapping a button invokes [onAction]. */
    fun bind(items: List<ActionItem>, onAction: (Int) -> Unit) {
        removeAllViews()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(PADDING_H), dp(PADDING_V), dp(PADDING_H), dp(PADDING_V))
        }
        val textColor = resources.getColor(
            if (dark) R.color.letter_text_dark else R.color.letter_text_light, null
        )
        val tint = resources.getColor(if (dark) R.color.icon_dark else R.color.icon_light, null)
        items.forEachIndexed { index, item ->
            row.addView(
                buttonFor(item, textColor, tint, onAction),
                LinearLayout.LayoutParams(0, dp(BUTTON_H), 1f).apply {
                    if (index < items.lastIndex) marginEnd = dp(SPACING)
                }
            )
        }
        addView(row)
    }

    /** Re-applies enabled/alpha/icon tint without re-rendering (selection changed). */
    fun refreshEnabled(items: List<ActionItem>) {
        if (childCount == 0) return
        val row = getChildAt(0) as? LinearLayout ?: return
        val tint = resources.getColor(if (dark) R.color.icon_dark else R.color.icon_light, null)
        val dimTint = (tint and 0x00FFFFFF) or (0x66 shl 24)
        items.forEachIndexed { index, item ->
            val button = row.getChildAt(index)
            button.isEnabled = item.enabled
            button.isClickable = item.enabled
            button.alpha = if (item.enabled) 1f else 0.4f
            (button as? LinearLayout)?.let { b ->
                (b.getChildAt(0) as? ImageView)?.imageTintList =
                    ColorStateList.valueOf(if (item.enabled) tint else dimTint)
            }
        }
    }

    private fun buttonFor(
        item: ActionItem,
        textColor: Int,
        tint: Int,
        onAction: (Int) -> Unit
    ): LinearLayout {
        val dimTint = (tint and 0x00FFFFFF) or (0x66 shl 24)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = item.enabled
            isEnabled = item.enabled
            alpha = if (item.enabled) 1f else 0.4f
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                val outVal = android.util.TypedValue()
                context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, outVal, true
                )
                if (outVal.resourceId != 0) {
                    foreground = resources.getDrawable(outVal.resourceId, null)
                }
            }
            addView(
                ImageView(context).apply {
                    setImageResource(item.iconRes)
                    imageTintList = ColorStateList.valueOf(if (item.enabled) tint else dimTint)
                },
                LinearLayout.LayoutParams(dp(20), dp(20))
            )
            addView(
                TextView(context).apply {
                    text = item.label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    setTextColor(textColor)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            )
            if (item.enabled) setOnClickListener { onAction(item.id) }
        }
    }

    fun menuWidthPx() = dp(PADDING_H) * 2 + 4 * dp(BUTTON_W) + 3 * dp(SPACING)

    fun menuHeightPx() = dp(PADDING_V) * 2 + dp(BUTTON_H)

    /** Shrinks away, then removes this view from its parent. */
    fun dismiss() {
        if (dismissing) return
        dismissing = true
        animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(110)
            .withEndAction { (parent as? ViewManager)?.removeView(this) }
            .start()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private companion object {
        const val PADDING_H = 10
        const val PADDING_V = 8
        const val BUTTON_W = 72
        const val BUTTON_H = 74
        const val SPACING = 6
    }
}