package com.romannepali.keyboard.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.R

enum class ClipAction { SELECT_ALL, CUT, COPY, PASTE }

/** One tappable editing action rendered at the front of the clipboard strip. */
data class ActionItem(
    val id: Int,
    val iconRes: Int,
    val label: String,
    val enabled: Boolean
)

/**
 * Slim horizontal strip showing the most recent clipboard clips, preceded by the
 * four editing actions (Select all / Copy / Paste / Cut).
 *
 * Tap a clip → its text is pasted into the editor. Tap the trash icon → the clip
 * is removed. Tap an action icon → the action runs against the focused editor.
 * Actions are dimmed (and inert) when they don't apply, e.g. Copy with no
 * selection.
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
        text = context.getString(R.string.no_clipboard)
        textSize = 13f
        setTextColor(resources.getColor(R.color.icon_light, null))
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    init {
        isHorizontalScrollBarEnabled = false
        isSmoothScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        addView(container)
    }

    // Skip pointless rebuilds: rebinding the exact same history on every pane
    // refresh is what made the strip flicker / stutter while scrolling.
    private var lastHistorySignature: String? = null
    private var lastHasSelection = false
    private var lastDark = true

    // Action chips by ClipAction.ordinal, so enabled state can be refreshed
    // in place when the selection changes without rebuilding the strip.
    private val actionChips = LinkedHashMap<Int, LinearLayout>()

    /** @param onPaste called with the clip text when the user taps a chip.
     *  @param hasSelection whether the editor currently has highlighted text.
     *  @param onAction called with the editing action when an action chip is tapped. */
    fun bind(
        manager: ClipboardManager,
        dark: Boolean,
        hasSelection: Boolean,
        onPaste: (String) -> Unit,
        onDelete: (String) -> Unit,
        onAction: (ClipAction) -> Unit
    ) {
        val items = manager.getHistory().take(20)
        val signature = items.joinToString("\u0000") { it.text }
        if (signature == lastHistorySignature && dark == lastDark) {
            // Nothing structural changed — just keep the action enabled states
            // in sync with the current selection.
            if (lastHasSelection != hasSelection) {
                lastHasSelection = hasSelection
                updateActionEnabled(hasSelection)
            }
            return
        }
        lastHistorySignature = signature
        lastHasSelection = hasSelection
        lastDark = dark

        val textRes = if (dark) R.color.letter_text_dark else R.color.letter_text_light
        val chipBg = if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light
        val tint = resources.getColor(if (dark) R.color.icon_dark else R.color.icon_light, null)

        container.removeAllViews()
        actionChips.clear()

        // --- editing actions, always present, dimmed when unavailable ---
        actionItems(hasSelection).forEach { item ->
            val chip = actionChip(item, chipBg, textRes, tint, onAction)
            actionChips[item.id] = chip
            container.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(CHIP_HEIGHT_DP)
            ).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        container.addView(divider(tint))

        if (items.isEmpty()) {
            container.addView(emptyLabel)
            post { smoothScrollTo(0, 0) }
            return
        }

        items.forEachIndexed { index, clip ->
            val display = clip.text.replace("\n", " ").trim()
            val chip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = resources.getDrawable(chipBg, null)
                setPadding(dp(4), 0, dp(2), 0)
                setOnClickListener { onPaste(clip.text) }
            }

            val label = TextView(context).apply {
                text = if (display.length > 32) display.take(32) + "\u2026" else display
                setTextColor(resources.getColor(textRes, null))
                textSize = 13f
                gravity = Gravity.CENTER
                isSingleLine = true
                maxWidth = dp(160)
                setPadding(dp(6), dp(5), dp(2), dp(5))
            }
            chip.addView(label)

            val remove = ImageButton(context).apply {
                background = null
                setImageResource(R.drawable.ic_trash)
                imageTintList = ColorStateList.valueOf(tint)
                contentDescription = "Delete clipboard item"
                setPadding(0, 0, 0, 0)
                setOnClickListener { onDelete(clip.text) }
            }
            chip.addView(remove, LinearLayout.LayoutParams(dp(24), dp(24)))

            chip.alpha = 0f
            chip.scaleX = 0.8f
            chip.scaleY = 0.8f
            chip.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 12L)
                .setDuration(160)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()

            container.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(CHIP_HEIGHT_DP)
            ).apply { marginStart = dp(2); marginEnd = dp(2) })
        }

        // Glide back to the first clip instead of snapping.
        post { smoothScrollTo(0, 0) }
    }

    // ------------------------------------------------------------------
    // Editing actions
    // ------------------------------------------------------------------

    private fun actionItems(hasSelection: Boolean): List<ActionItem> {
        val historyNonEmpty = lastHistorySignature != null &&
            lastHistorySignature!!.isNotEmpty()
        return listOf(
            ActionItem(
                ClipAction.SELECT_ALL.ordinal, R.drawable.ic_select_all,
                "Select all", enabled = true
            ),
            ActionItem(
                ClipAction.COPY.ordinal, R.drawable.ic_content_copy,
                "Copy", enabled = hasSelection
            ),
            ActionItem(
                ClipAction.PASTE.ordinal, R.drawable.ic_content_paste,
                "Paste", enabled = historyNonEmpty
            ),
            ActionItem(
                ClipAction.CUT.ordinal, R.drawable.ic_content_cut,
                "Cut", enabled = hasSelection
            )
        )
    }

    private fun actionChip(
        item: ActionItem,
        chipBg: Int,
        textRes: Int,
        tint: Int,
        onAction: (ClipAction) -> Unit
    ): LinearLayout {
        val dimTint = (tint and 0x00FFFFFF) or (0x66 shl 24)
        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = resources.getDrawable(chipBg, null)
            setPadding(dp(8), 0, dp(8), 0)
            isEnabled = item.enabled
            isClickable = item.enabled
            alpha = if (item.enabled) 1f else 0.4f
            // Listener is always attached; isEnabled/isClickable gate the tap so
            // the state can be toggled later without reattaching.
            setOnClickListener { onAction(ClipAction.values()[item.id]) }
        }
        val icon = ImageButton(context).apply {
            background = null
            setImageResource(item.iconRes)
            imageTintList = ColorStateList.valueOf(if (item.enabled) tint else dimTint)
            contentDescription = item.label
            setPadding(0, 0, 0, 0)
        }
        chip.addView(icon, LinearLayout.LayoutParams(dp(18), dp(18)))
        chip.addView(TextView(context).apply {
            text = item.label
            textSize = 12f
            isSingleLine = true
            gravity = Gravity.CENTER
            setTextColor(resources.getColor(textRes, null))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(5) })
        return chip
    }

    /** Flips action chips between enabled/disabled in place (selection changed). */
    private fun updateActionEnabled(hasSelection: Boolean) {
        val tint = resources.getColor(
            if (lastDark) R.color.icon_dark else R.color.icon_light, null
        )
        val dimTint = (tint and 0x00FFFFFF) or (0x66 shl 24)
        actionItems(hasSelection).forEach { item ->
            val chip = actionChips[item.id] ?: return@forEach
            chip.isEnabled = item.enabled
            chip.isClickable = item.enabled
            chip.alpha = if (item.enabled) 1f else 0.4f
            (chip.getChildAt(0) as? ImageButton)?.imageTintList =
                ColorStateList.valueOf(if (item.enabled) tint else dimTint)
        }
    }

    private fun divider(tint: Int): View = View(context).apply {
        setBackgroundColor((tint and 0x00FFFFFF) or (0x33 shl 24))
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(22)).apply {
            marginStart = dp(4)
            marginEnd = dp(6)
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}

/** Chip height. Fits inside the 36dp landscape bar as well as the 44dp portrait one. */
private const val CHIP_HEIGHT_DP = 30
