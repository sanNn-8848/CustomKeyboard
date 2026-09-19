package com.romannepali.keyboard.clipboard

import android.content.Context
import android.content.res.ColorStateList
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
import com.romannepali.keyboard.R
import kotlin.math.abs

enum class ClipAction { SELECT_ALL, CUT, COPY, PASTE }

/**
 * Slim horizontal pane that shows the most recent clipboard clips.
 * Tap a clip → its text is pasted into the editor. Tap the ✕ → the clip is removed.
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
        val textRes = if (dark) R.color.letter_text_dark else R.color.letter_text_light
        val chipBg = if (dark) R.drawable.bg_suggestion_chip_dark else R.drawable.bg_suggestion_chip_light
        val tint = resources.getColor(if (dark) R.color.icon_dark else R.color.icon_light, null)

        val items = manager.getHistory().take(20)
        val signature = items.joinToString("\u0000") { it.text }
        if (signature == lastHistorySignature) return
        lastHistorySignature = signature

        container.removeAllViews()

        fun actionChip(label: String, click: () -> Unit) {
            container.addView(
                TextView(context).apply {
                    text = label
                    background = resources.getDrawable(chipBg, null)
                    setTextColor(resources.getColor(textRes, null))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener { click() }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(32)
                ).apply { marginStart = dp(2); marginEnd = dp(2) }
            )
        }

        actionChip("Select all") { onAction(ClipAction.SELECT_ALL) }
        actionChip("Cut") { onAction(ClipAction.CUT) }
        actionChip("Copy") { onAction(ClipAction.COPY) }
        actionChip("Paste") { onAction(ClipAction.PASTE) }

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
            }
            attachClipGesture(
                chip,
                dark,
                clipboardEmpty = items.isEmpty(),
                hasSelection = hasSelection,
                onTap = { onPaste(clip.text) },
                onAction = onAction
            )

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
    // Gesture selection menu
    // ------------------------------------------------------------------

    private var gestureMenu: GlassGestureMenu? = null
    private var gestureMenuTopY = 0

    /**
     * Tap a clip chip to paste; hold it and the glass gesture menu opens with
     * the editing actions. While it is open the finger steers the highlight
     * pill; releasing on a row runs that action, releasing nowhere cancels.
     */
    private fun attachClipGesture(
        chip: View,
        dark: Boolean,
        clipboardEmpty: Boolean,
        hasSelection: Boolean,
        onTap: () -> Unit,
        onAction: (ClipAction) -> Unit
    ) {
        var lastY = 0f
        var longFired = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        val fire = Runnable {
            longFired = true
            // Keep the strip's scroll view from stealing the live gesture.
            parent?.requestDisallowInterceptTouchEvent(true)
            openClipMenu(chip, dark, clipboardEmpty, hasSelection, onAction)
        }
        chip.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.rawY
                    longFired = false
                    v.removeCallbacks(fire)
                    v.postDelayed(fire, 280L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longFired) {
                        gestureMenu?.let { it.onGestureMove(event.rawY - gestureMenuTopY) }
                    } else if (abs(event.rawY - lastY) > slop) {
                        v.removeCallbacks(fire)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(fire)
                    if (longFired) {
                        gestureMenu?.let { m ->
                            val chosen = m.activateItem()
                            dismissMenu(chosen?.let { c -> { onAction(ClipAction.values()[c.id]) } })
                        }
                    } else {
                        onTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(fire)
                    dismissMenu(null)
                    true
                }
                else -> true
            }
        }
    }

    /** Anchors the floating glass menu next to the pressed clip. */
    private fun openClipMenu(
        chip: View,
        dark: Boolean,
        clipboardEmpty: Boolean,
        hasSelection: Boolean,
        onAction: (ClipAction) -> Unit
    ) {
        val root = rootView as? FrameLayout ?: return
        if (gestureMenu != null) return

        val itemList = listOf(
            GestureMenuItem(
                ClipAction.PASTE.ordinal, R.drawable.ic_content_paste,
                "Paste", enabled = !clipboardEmpty
            ),
            GestureMenuItem(
                ClipAction.COPY.ordinal, R.drawable.ic_content_copy,
                "Copy", enabled = hasSelection
            ),
            GestureMenuItem(
                ClipAction.SELECT_ALL.ordinal, R.drawable.ic_select_all,
                "Select all", enabled = true
            ),
            GestureMenuItem(
                ClipAction.CUT.ordinal, R.drawable.ic_content_cut,
                "Cut", enabled = hasSelection
            )
        )

        val menu = GlassGestureMenu(context)
        menu.setTheme(dark)
        menu.bind(itemList, defaultIndex = 2)

        val w = menu.menuWidthPx()
        val h = menu.menuHeightPx()

        val rootLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        val chipLoc = IntArray(2)
        chip.getLocationInWindow(chipLoc)

        val chipCenterX = chipLoc[0] - rootLoc[0] + chip.width / 2
        val anchorTop = chipLoc[1] - rootLoc[1]
        val gap = dp(8)

        val left = (chipCenterX - w / 2).coerceIn(0, (root.width - w).coerceAtLeast(0))
        val top = if (anchorTop - gap >= h) {
            anchorTop - gap - h
        } else {
            (anchorTop + chip.height + gap).coerceAtMost((root.height - h).coerceAtLeast(0))
        }

        menu.pivotX = (w / 2).toFloat()
        menu.pivotY = 0f
        root.addView(
            menu,
            FrameLayout.LayoutParams(w, h).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = left
                topMargin = top
            }
        )

        menu.alpha = 0f
        menu.scaleX = 0.92f
        menu.scaleY = 0.92f
        menu.translationY = dp(6).toFloat()
        menu.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(160)
            .setInterpolator(OvershootInterpolator(1.05f))
            .start()

        menu.post {
            val loc = IntArray(2)
            menu.getLocationInWindow(loc)
            gestureMenuTopY = loc[1]
        }
        gestureMenu = menu
    }

    private fun dismissMenu(after: (() -> Unit)? = null) {
        val m = gestureMenu
        if (m == null) {
            after?.invoke()
            return
        }
        gestureMenu = null
        after?.invoke()
        m.dismiss()
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()
}