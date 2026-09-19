package com.romannepali.keyboard.clipboard

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.romannepali.keyboard.R

enum class ClipAction { SELECT_ALL, CUT, COPY, PASTE }

/**
 * Slim horizontal pane that shows the most recent clipboard clips.
 * Tap a clip → its text is pasted into the editor. Tap the ✕ → the clip is removed.
 * The "Actions" button opens a floating glass panel (above the keyboard) with the
 * four editing actions as static, directly-tappable buttons.
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

    private var actionPanel: GlassActionPanel? = null
    private var panelOpen = false

    /** @param onAction editing toolbar: select all / cut / copy / paste.
     *  @param onPaste called with the clip text when the user taps a chip.
     *  @param hasSelection whether the editor currently has highlighted text
     *  (disables Copy / Cut while nothing is selected). */
    fun bind(
        manager: ClipboardManager,
        dark: Boolean,
        hasSelection: Boolean,
        onAction: (ClipAction) -> Unit,
        onPaste: (String) -> Unit,
        onDelete: (String) -> Unit
    ) {
        val items = manager.getHistory().take(20)
        val signature = items.joinToString("\u0000") { it.text }
        if (signature == lastHistorySignature) {
            // Keep the open action panel in sync with the selection state.
            if (lastHasSelection != hasSelection) {
                lastHasSelection = hasSelection
                actionPanel?.refreshEnabled(actionItems(hasSelection))
            }
            return
        }
        lastHistorySignature = signature
        lastHasSelection = hasSelection
        closeActionPanel()

        val textRes = if (dark) R.color.letter_text_dark else R.color.letter_text_light
        val chipBg = if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light
        val tint = resources.getColor(if (dark) R.color.icon_dark else R.color.icon_light, null)

        container.removeAllViews()

        // One "Actions" button instead of four scattered chips: tapping it flips
        // the floating glass panel with Select All / Copy / Paste / Cut.
        container.addView(
            TextView(context).apply {
                text = "Actions"
                background = resources.getDrawable(chipBg, null)
                setTextColor(resources.getColor(textRes, null))
                setCompoundDrawablesWithIntrinsicBounds(
                    resources.getDrawable(R.drawable.ic_actions, null), null, null, null
                )
                compoundDrawablePadding = dp(4)
                getCompoundDrawables()?.firstOrNull()?.setTint(tint)
                textSize = 13f
                gravity = Gravity.CENTER
                isSingleLine = true
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener {
                    if (panelOpen) closeActionPanel() else openActionPanel(dark, hasSelection, onAction)
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
            ).apply { marginStart = dp(2); marginEnd = dp(4) }
        )

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
                dp(32)
            ).apply { marginStart = dp(2); marginEnd = dp(2) })
        }

        // Glide back to the first clip instead of snapping.
        post { smoothScrollTo(0, 0) }
    }

    // ------------------------------------------------------------------
    // Floating glass action panel
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

    /** Anchors the glass panel just above the keyboard, centered. */
    private fun openActionPanel(
        dark: Boolean,
        hasSelection: Boolean,
        onAction: (ClipAction) -> Unit
    ) {
        val root = rootView as? FrameLayout ?: return
        closeActionPanel()

        val panel = GlassActionPanel(context)
        panel.setTheme(dark)
        panel.bind(actionItems(hasSelection)) { id ->
            closeActionPanel()
            onAction(ClipAction.values()[id])
        }

        val w = panel.menuWidthPx()
        val h = panel.menuHeightPx()
        val left = ((root.width - w) / 2).coerceAtLeast(0)
        val top = dp(4)

        panel.pivotX = (w / 2).toFloat()
        panel.pivotY = 0f
        root.addView(
            panel,
            FrameLayout.LayoutParams(w, h).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = left
                topMargin = top
            }
        )

        panel.alpha = 0f
        panel.scaleX = 0.92f
        panel.scaleY = 0.92f
        panel.translationY = dp(6).toFloat()
        panel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(160)
            .setInterpolator(OvershootInterpolator(1.05f))
            .start()

        actionPanel = panel
        panelOpen = true
    }

    private fun closeActionPanel() {
        val panel = actionPanel
        if (panel == null) {
            panelOpen = false
            return
        }
        panelOpen = false
        actionPanel = null
        panel.dismiss()
    }

    override fun onDetachedFromWindow() {
        closeActionPanel()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}