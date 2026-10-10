package com.romannepali.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Typeface
import android.media.SoundPool
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class GboardKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // Functional key tokens (never sent as typed text).
    private val SHIFT = "KEY_SHIFT"
    private val BACKSPACE = "KEY_BACKSPACE"
    private val SPACE = "KEY_SPACE"
    private val SYMBOLS = "KEY_SYMBOLS"
    private val DOT = "KEY_DOT"
    private val ENTER = "KEY_ENTER"
    private val COMMA = "KEY_COMMA"

    private data class KeyDef(val label: String?, val weight: Float)

    private val lettersRows: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef("q", 1f), KeyDef("w", 1f), KeyDef("e", 1f), KeyDef("r", 1f),
            KeyDef("t", 1f), KeyDef("y", 1f), KeyDef("u", 1f), KeyDef("i", 1f),
            KeyDef("o", 1f), KeyDef("p", 1f)
        ),
        listOf(
            KeyDef(null, 0.5f), KeyDef("a", 1f), KeyDef("s", 1f), KeyDef("d", 1f),
            KeyDef("f", 1f), KeyDef("g", 1f), KeyDef("h", 1f), KeyDef("j", 1f),
            KeyDef("k", 1f), KeyDef("l", 1f), KeyDef(null, 0.5f)
        ),
        listOf(
            KeyDef(SHIFT, 1.6f), KeyDef("z", 1f), KeyDef("x", 1f), KeyDef("c", 1f),
            KeyDef("v", 1f), KeyDef("b", 1f), KeyDef("n", 1f), KeyDef("m", 1f),
            KeyDef(BACKSPACE, 1.6f)
        ),
        listOf(
            KeyDef(SYMBOLS, 1.6f), KeyDef(COMMA, 1f), KeyDef(SPACE, 4.6f),
            KeyDef(DOT, 1f), KeyDef(ENTER, 1.6f)
        )
    )

    private val symbolRows: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef("1", 1f), KeyDef("2", 1f), KeyDef("3", 1f), KeyDef("4", 1f),
            KeyDef("5", 1f), KeyDef("6", 1f), KeyDef("7", 1f), KeyDef("8", 1f),
            KeyDef("9", 1f), KeyDef("0", 1f)
        ),
        listOf(
            KeyDef("!", 1f), KeyDef("@", 1f), KeyDef("#", 1f), KeyDef("$", 1f),
            KeyDef("%", 1f), KeyDef("^", 1f), KeyDef("&", 1f), KeyDef("*", 1f),
            KeyDef("(", 1f), KeyDef(")", 1f)
        ),
        listOf(
            KeyDef("-", 1f), KeyDef("_", 1f), KeyDef("=", 1f), KeyDef("+", 1f),
            KeyDef("[", 1f), KeyDef("]", 1f), KeyDef("{", 1f), KeyDef("}", 1f),
            KeyDef("\\", 1f), KeyDef("|", 1f)
        ),
        listOf(
            KeyDef(SYMBOLS, 1.6f), KeyDef(COMMA, 1f), KeyDef(SPACE, 4.6f),
            KeyDef(DOT, 1f), KeyDef(ENTER, 1.6f)
        )
    )

    /** Long-press secondary characters shown top-right and inserted on a 250ms hold. */
    private val ALT_SYMBOLS = mapOf(
        'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
        'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
        'a' to "@", 's' to "#", 'd' to "–", 'f' to "%", 'g' to "&",
        'h' to "'", 'j' to "\"", 'k' to "(", 'l' to ")",
        'z' to "!", 'x' to "?", 'c' to "/", 'v' to ":", 'b' to ";",
        'n' to "€", 'm' to "-"
    )

    private class KeyParts(
        val container: FrameLayout,
        val main: TextView,
        val tag: TextView?,
        val popup: TextView
    )

    // Remember letter keys so we can re-render upper/lower case without a full rebuild.
    private val letterKeys = mutableListOf<Pair<KeyParts, String>>()

    // Every key knows its row so a pressed key's popup can be raised above neighbours.
    private val keyRows = HashMap<View, View>()

    // Track every key's delayed touch work so a rebuild/detach can cancel it
    // before the dying views are discarded.
    private val pendingKeyRunnables = mutableListOf<Pair<View, Runnable>>()

    // Number row sits slightly shorter than letter rows (Gboard style).
    private val NUMBER_ROW_WEIGHT = 0.92f
    // Landscape: slimmer number row so the compacted keyboard leaves more room.
    private val NUMBER_ROW_WEIGHT_LANDSCAPE = 0.70f

    // ---- Production layout spec (dp) ----
    private val SIDE_MARGIN_MIN = 4
    private val SIDE_MARGIN_DEFAULT = 6
    private val KEY_MIN = 30
    private val KEY_MAX = 55
    private val KEY_EDGE = 2          // slot gap math for keyboardSideMargin(); keys themselves paint a 2dp/side <inset>
    private val LETTER_ROW = 44
    private val LETTER_ROW_LANDSCAPE = 34

    // User keyboard-size slider: 0..100 -> 0.80x .. 1.25x (50 normal = 1.0x).
    private val SIZE_MIN = 0.80f
    private val SIZE_SPAN = 0.45f

    private fun sizeFactor(): Float =
        SIZE_MIN + Prefs.keyboardSize(context) / 100f * SIZE_SPAN
    private val CHIN = 20             // transparent safety chin for nav gestures
    private val CHIN_LANDSCAPE = 12   // landscape: reclaim height from the chin

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun numberRowWeight(): Float =
        if (isLandscape()) NUMBER_ROW_WEIGHT_LANDSCAPE else NUMBER_ROW_WEIGHT

    private fun chinPx(): Int = dp(if (isLandscape()) CHIN_LANDSCAPE else CHIN)

    // Tactile spec: 250ms long-press, tap vs 40ms hold-and-repeat for backspace.
    private val LONG_PRESS_TIMEOUT = 250L
    /** Comma -> Emoji is a deliberate gesture: hold well past the repeat
     *  threshold so typing a normal comma never triggers it. */
    private val EMOJI_HOLD_TIMEOUT_MS = 600L
    private val REPEAT_INTERVAL = 40L

    // Triple-pitch audio: letter (high), space (mid), backspace/enter (low).
    private val SOUND_LETTER = 0
    private val SOUND_SPACE = 1
    private val SOUND_DELETE = 2

    var isShifted = false
        private set
    private var isShiftLocked = false
    private var lastShiftTapAt = 0L

    private var isSymbols = false
    private var darkTheme = true

    private var lastAppliedSize = Prefs.keyboardSize(context)

    private var soundPool: SoundPool? = null
    private var soundLetter = 0
    private var soundSpace = 0
    private var soundDelete = 0

    private val readySounds = mutableSetOf<Int>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        darkTheme = Prefs.darkTheme(context)
        buildKeyboard()
    }

    var onKeyPressed: ((String) -> Unit)? = null

    /** Long-press events the IME may want to act on (e.g. the comma key). */
    var onEmojiRequested: (() -> Unit)? = null
    private var commaKeyHint: Button? = null
    private var emojiModeActive = false
    var onKeyLongPressed: ((String) -> Unit)? = null

    // ------------------------------------------------------------------
    // Sound: 3 short synthesized clicks (high / mid / low pitch).
    // ------------------------------------------------------------------

    private fun ensureSound() {
        if (soundPool != null) return

        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .build()

        pool.setOnLoadCompleteListener { _, sampleId, status ->
            post {
                if (soundPool !== pool) return@post
                if (status == 0) {
                    readySounds.add(sampleId)
                } else {
                    readySounds.remove(sampleId)
                }
            }
        }

        soundPool = pool
        soundLetter = pool.load(context, R.raw.key_click_letter, 1)
        soundSpace = pool.load(context, R.raw.key_click_space, 1)
        soundDelete = pool.load(context, R.raw.key_click_delete, 1)
    }

    private fun playSound(kind: Int) {
        if (!Prefs.sound(context)) return
        ensureSound()
        val pool = soundPool ?: return
        val stream = when (kind) {
            SOUND_SPACE -> soundSpace
            SOUND_DELETE -> soundDelete
            else -> soundLetter
        }
        if (stream == 0 || stream !in readySounds) return
        // Original click: single fixed pitch/volume, matching v1.1.16 and earlier.
        pool.play(stream, 0.8f, 0.8f, 1, 0, 1f)
    }

    // Haptics: crisp impulse for letters, longer/stronger for structural keys.
    private fun hapticStrong(view: View) {
        if (Prefs.vibration(context)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    private fun bumpRow(key: View) {
        keyRows[key]?.translationZ = dp(18).toFloat()
    }

    // Quick press-down squash; the release springs back over the top (Gboard feel).
    private fun pressScaleOn(view: View) {
        if (view.width == 0) return
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.animate().cancel()
        view.animate().scaleX(0.94f).scaleY(0.94f)
            .setDuration(60)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun pressScaleOff(view: View) {
        if (view.width == 0) return
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.animate().cancel()
        view.animate().scaleX(1f).scaleY(1f)
            .setDuration(250)
            .setInterpolator(OvershootInterpolator(1.8f))
            .start()
    }

    private fun postKeyDelayed(
        key: View,
        runnable: Runnable,
        delay: Long
    ) {
        pendingKeyRunnables.add(key to runnable)
        key.postDelayed(runnable, delay)
    }

    private fun cancelKeyRunnable(
        key: View,
        runnable: Runnable
    ) {
        key.removeCallbacks(runnable)
        pendingKeyRunnables.removeAll { it.first === key && it.second === runnable }
    }

    private fun cancelAllKeyWork() {
        pendingKeyRunnables.forEach { (key, runnable) ->
            key.removeCallbacks(runnable)
        }
        pendingKeyRunnables.clear()

        // Cancel popup/key animations as part of the same lifecycle boundary.
        keyRows.keys.forEach { key ->
            key.animate().cancel()
        }
    }

    // ------------------------------------------------------------------
    // Keyboard construction
    // ------------------------------------------------------------------

    private fun buildKeyboard() {
        cancelAllKeyWork()
        removeAllViews()
        letterKeys.clear()
        keyRows.clear()
        shiftKey = null
        enterKey = null
        setBackgroundColor(themeBackground())
        // Outer horizontal inset computed from the real screen width so a weight-1
        // key lands inside [KEY_MIN, KEY_MAX] dp; any slack becomes the side margin.
        val sideMargin = keyboardSideMargin()
        setPadding(sideMargin, 0, sideMargin, 0)

        val rows = if (isSymbols) symbolRows else lettersRows

        // Always-on number row (main layout, matching the symbol page's digits).
        if (!isSymbols) {
            val digitRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    numberRowWeight()
                )
            }
            "1234567890".forEach { c ->
                addKey(
                    digitRow,
                    c.toString(),
                    1f,
                    isLetter = false,
                    onPress = { onKeyPressed?.invoke(c.toString()) }
                )
            }
            addView(digitRow)
        }

        rows.forEach { rowEntries ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            rowEntries.forEach { entry ->
                if (entry.label != null) {
                    addEntry(row, entry)
                } else {
                    addSpacer(row, entry.weight)
                }
            }

            addView(row)
        }

        // Transparent safety chin isolates the keys from system nav gestures.
        addView(
            View(context),
            LayoutParams(LayoutParams.MATCH_PARENT, chinPx())
        )

        refreshKeyLabels()

        // Height is resolved by onMeasure from the window/wrapper constraints,
        // so no explicit layoutParams height is set here (responsive sizing).
    }

    private fun keyboardHeightPx(): Int {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Landscape layout policy: 34dp letter rows (portrait keeps LETTER_ROW).
        // At 34dp: 34*4.70 + 12 chin = ~172dp keyboard body -> total ~222dp with
        // the compacted landscape carousel/suggestion strips (layout-land xml).
        val letterRowDp = if (landscape) LETTER_ROW_LANDSCAPE else LETTER_ROW
        val letterRow = dp(letterRowDp) * sizeFactor()
        val withNumber = !isSymbols
        val totalWeight = 4f + if (withNumber) numberRowWeight() else 0f
        return (letterRow * totalWeight).toInt() + chinPx()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation must not tear down the key hierarchy: keeping it alive lets
        // shift/symbol state, the current word, and suggestions survive. Just
        // re-apply the side padding for the new width; onMeasure re-fits the
        // height to the new orientation on the next layout pass.
        post {
            setPadding(keyboardSideMargin(), 0, keyboardSideMargin(), 0)
            requestLayout()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        cancelAllKeyWork()

        soundPool?.release()
        soundPool = null

        readySounds.clear()

        soundLetter = 0
        soundSpace = 0
        soundDelete = 0
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // The keyboard sizes itself to its preferred height (scale + orientation
        // aware), but never exceeds what the parent/window actually allows. This
        // replaces the old fixed-height/fixed-168dp behavior so the keyboard
        // stays consistent in portrait and landscape and after rotations.
        val preferred = keyboardHeightPx()
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val targetHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(preferred, heightSize)
            else -> preferred
        }
        // Re-measure with the resolved height so the weighted rows distribute
        // over the full target instead of a zero-height first pass.
        val heightSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, heightSpec)
        setMeasuredDimension(measuredWidth, targetHeight)
    }

    /**
     * Computes the keyboard's horizontal inset from the actual screen width.
     *
     * Key width = (available width - total gaps) / keys in row, clamped to
     * [KEY_MIN, KEY_MAX] scaled by the user's keyboard size. If the natural
     * width falls outside that band, the
     * side margin absorbs the slack (landscape/tablet -> centered keyboard,
     * tiny screens -> margins kept at SIDE_MARGIN_MIN). Weights then apply the
     * same scale to every row, so columns stay aligned.
     */
    private fun keyboardSideMargin(): Int {
        val screenW = resources.displayMetrics.widthPixels
        val defaultMargin = dp(SIDE_MARGIN_DEFAULT)
        val slots = 10 // a letter row carries 10 weight-1 slots (spacers included)
        val edgePx = dp(KEY_EDGE) * 2
        val naturalSlot = (screenW - 2 * defaultMargin) / slots
        val visible = naturalSlot - edgePx
        val unit = visible.coerceIn(
            (dp(KEY_MIN) * sizeFactor()).toInt(),
            (dp(KEY_MAX) * sizeFactor()).toInt()
        )
        if (unit == visible) return defaultMargin
        val wanted = (screenW - slots * (unit + edgePx)) / 2
        return maxOf(wanted, dp(SIDE_MARGIN_MIN))
    }

    private fun addEntry(row: LinearLayout, entry: KeyDef) {
        when (entry.label) {
            SHIFT -> addActionKey(
                row,
                entry.weight,
                iconRes = R.drawable.ic_shift,
                click = { toggleShift() }
            )
            BACKSPACE -> addActionKey(
                row,
                entry.weight,
                iconRes = R.drawable.ic_backspace,
                click = { onKeyPressed?.invoke("⌫") },
                repeat = true,
                strong = true,
                sound = SOUND_DELETE
            )
            SPACE -> addSpaceKey(row, entry.weight)
            SYMBOLS -> addActionKey(
                row,
                entry.weight,
                label = if (isSymbols) "ABC" else "?123",
                click = { toggleSymbols() }
            )
            COMMA -> {
                val commaKey = addActionKey(
                    row,
                    entry.weight,
                    label = ",",
                    hEnd = 8, // safety gap before the space bar
                    click = { onKeyPressed?.invoke(",") },
                    onLongPress = {
                        // Undo the comma typed on touch-down, then hand the gesture
                        // to the IME (opens the Emoji search pane).
                        onKeyPressed?.invoke("⌫")
                        onEmojiRequested?.invoke()
                    },
                    // Longer than the repeat threshold so ordinary typing of a comma
                    // never opens the Emoji pane by accident.
                    holdTimeoutMs = EMOJI_HOLD_TIMEOUT_MS
                )
                commaKeyHint = commaKey as? Button
                styleCommaHint()
            }
            DOT -> addActionKey(
                row,
                entry.weight,
                label = ".",
                hStart = 8, // safety gap after the space bar
                click = { onKeyPressed?.invoke(".") }
            )
            ENTER -> addActionKey(
                row,
                entry.weight,
                iconRes = R.drawable.ic_enter,
                tint = accentColor(),
                click = { onKeyPressed?.invoke("↵") },
                strong = true,
                sound = SOUND_DELETE
            ).also { enterKey = it }.also { applyEnterActionIcon() }

            else -> {
                val text = entry.label ?: return
                val isLetter = text.length == 1 && text[0] in 'a'..'z'
                val parts = addKey(
                    row,
                    labelFor(text),
                    entry.weight,
                    isLetter = true,
                    altTag = ALT_SYMBOLS[text[0]]?.toString(),
                    onAltPress = {
                        // The letter already committed on touch-down; replace it with
                        // the secondary character so a long-press inserts the tag.
                        ALT_SYMBOLS[text[0]]?.let { alt ->
                            onKeyPressed?.invoke("⌫")
                            onKeyPressed?.invoke(alt.toString())
                        }
                    },
                    onPress = {
                        // Any letter press breaks a shift double-tap window.
                        lastShiftTapAt = 0L
                        val upper = isShifted || isShiftLocked
                        val sent = if (isLetter && upper) text.uppercase() else text
                        onKeyPressed?.invoke(sent)
                        if (isLetter && upper && !isShiftLocked) {
                            isShifted = false
                            refreshKeyLabels()
                            styleShiftIcon()
                        }
                    }
                )
                if (isLetter) {
                    letterKeys.add(parts to text)
                }
            }
        }
    }

    private var shiftKey: View? = null

    private var enterKey: View? = null

    private var enterAction: Int = 0

    private fun addKey(
        row: LinearLayout,
        label: String,
        weight: Float,
        isLetter: Boolean,
        altTag: String? = null,
        onAltPress: (() -> Unit)? = null,
        onPress: () -> Unit
    ): KeyParts {
        val key = FrameLayout(context)
        key.clipChildren = false
        key.clipToPadding = false
        key.background = resources.getDrawable(
            if (darkTheme) R.drawable.key_bg else R.drawable.key_bg_light,
            null
        )
        keyRows[key] = row
        // Full-cell hit area: the views tile edge-to-edge so every pixel
        // between keys belongs to a key (no mistouch dead zones). The visible
        // 4dp gap is painted by the background's <inset>, which keeps the
        // slot-width math in keyboardSideMargin() honest.
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        )

        val main = TextView(context).apply {
            text = label
            textSize = (if (isLetter) 18f else 17f) * sizeFactor()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(letterTextColor())
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
            // 2dp downward offset so the top-right tag keeps the glyph visually centered.
            translationY = dp(1).toFloat()
        }
        key.addView(main, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val tag: TextView? = altTag?.let { alt ->
            TextView(context).apply {
                text = alt
                textSize = 10f * sizeFactor()
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(secondaryTextColor())
                alpha = 0.5f
                gravity = Gravity.CENTER
                setIncludeFontPadding(false)
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                )
                lp.setMargins(0, dp(3), dp(4), 0)
                key.addView(this, lp)
            }
        }

        // Press pop-up: 130% height cap rising ~8dp above the finger, 15ms fade-out.
        val popup = TextView(context).apply {
            text = label
            textSize = 20f * sizeFactor()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(letterTextColor())
            gravity = Gravity.CENTER
            setIncludeFontPadding(false)
            setBackgroundResource(R.drawable.key_bg_action)
            elevation = dp(10).toFloat()
            val lp = FrameLayout.LayoutParams(
                dp(46),
                dp(46),
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            )
            lp.bottomMargin = dp(4)
            key.addView(this, lp)
            pivotY = dp(46).toFloat()   // grows upward from its bottom edge
            visibility = View.GONE
            alpha = 0f
        }

        var longPressFired = false
        val longPressRunnable = Runnable {
            longPressFired = true
            if (altTag != null && onAltPress != null) {
                hapticStrong(key)
                popup.text = altTag
                onAltPress()
            }
        }

        // Commit on touch-down (like Gboard) so rapid, overlapping taps can never be
        // cancelled by touch-slop or by a second finger landing first. An additional
        // pointer is committed too, because fast two-thumb typing overlaps pointers.
        var ownerPointerId = 0
        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        ownerPointerId = event.getPointerId(0)
                        longPressFired = false
                        cancelKeyRunnable(key, longPressRunnable)
                        postKeyDelayed(key, longPressRunnable, LONG_PRESS_TIMEOUT)
                    }
                    // Commit FIRST: the character must never wait behind
                    // sound, animation, or popup work (fast-typing latency).
                    onPress()
                    key.isPressed = true
                    pressScaleOn(key)
                    playSound(SOUND_LETTER)
                    showPopup(popup, main.text, key)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Track THIS key's own pointer in view-local coordinates:
                    // rawX/rawY follow pointer 0 and lie about position the
                    // moment a second finger lands (fast two-thumb typing).
                    val idx = event.findPointerIndex(ownerPointerId)
                    if (idx >= 0) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        val inside =
                            x >= 0f && y >= 0f && x < key.width && y < key.height
                        key.isPressed = inside
                        if (!inside) {
                            cancelKeyRunnable(key, longPressRunnable)
                            hidePopup(popup, key)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelKeyRunnable(key, longPressRunnable)
                    key.isPressed = false
                    pressScaleOff(key)
                    hidePopup(popup, key)
                    true
                }

                else -> true
            }
        }

        row.addView(key)
        return KeyParts(key, main, tag, popup)
    }

    private fun showPopup(popup: TextView, label: CharSequence, key: View) {
        popup.text = label
        popup.pivotX = popup.width / 2f
        popup.pivotY = popup.height.toFloat()
        popup.animate().cancel()
        popup.scaleX = 1f
        popup.scaleY = 0.3f
        popup.translationY = -dp(46).toFloat()
        popup.alpha = 1f
        bumpRow(key)
        popup.visibility = View.VISIBLE
        // Spring up past the resting height, then settle back.
        popup.animate().scaleY(1.3f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(2.4f))
            .start()
    }

    private fun hidePopup(popup: TextView, key: View) {
        keyRows[key]?.translationZ = 0f
        if (popup.visibility != View.VISIBLE) return
        popup.animate()
            .alpha(0f)
            .scaleY(0.5f)
            .translationY(-dp(14).toFloat())
            .setDuration(110)
            .withEndAction {
                popup.visibility = View.GONE
                popup.scaleX = 1f
                popup.scaleY = 1f
                popup.translationY = 0f
            }
    }

    private fun addActionKey(
        row: LinearLayout,
        weight: Float,
        label: String = "",
        iconRes: Int? = null,
        tint: Int? = null,
        click: () -> Unit,
        onLongPress: (() -> Unit)? = null,
        repeat: Boolean = false,
        holdTimeoutMs: Long = LONG_PRESS_TIMEOUT,
        strong: Boolean = false,
        sound: Int = SOUND_LETTER,
        hStart: Int = 0,
        hEnd: Int = 0
    ): View {
        val key: View = if (iconRes != null) {
            val container = FrameLayout(context)
            container.background = resources.getDrawable(
                if (darkTheme) R.drawable.key_bg_action else R.drawable.key_bg_action_light,
                null
            )
            val icon = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dp(38),
                    dp(38),
                    Gravity.CENTER
                )
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(tint ?: iconColor())
                isClickable = false
                isFocusable = false
            }
            container.addView(icon)
            container
        } else {
            styleKey(Button(context), false).apply { text = label }
        }
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(dp(hStart), 0, dp(hEnd), 0)
        }
        keyRows[key] = row

        if (iconRes == R.drawable.ic_shift) {
            shiftKey = key
            styleShiftIcon()
        }

        var longPressFired = false

        val repeatRunnable = object : Runnable {
            override fun run() {
                if (longPressFired && repeat) {
                    playSound(sound)
                    click()
                    postKeyDelayed(key, this, REPEAT_INTERVAL)
                }
            }
        }

        val longPressRunnable = Runnable {
            longPressFired = true
            if (onLongPress != null) {
                onLongPress()
            } else if (repeat) {
                click()
                postKeyDelayed(key, repeatRunnable, REPEAT_INTERVAL)
            }
        }

        // Action keys also commit on touch-down for instant response. Backspace does
        // a first delete on down, then starts repeating once the long-press fires.
        var ownerPointerId = 0
        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        ownerPointerId = event.getPointerId(0)
                        longPressFired = false
                        if ((repeat || onLongPress != null) && event.pointerCount == 1) {
                            postKeyDelayed(key, longPressRunnable, holdTimeoutMs)
                        }
                    }
                    // Commit FIRST so feedback work can't delay the action.
                    click()
                    key.isPressed = true
                    pressScaleOn(key)
                    playSound(sound)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (longPressFired) {
                        true
                    } else {
                        val idx = event.findPointerIndex(ownerPointerId)
                        if (idx >= 0) {
                            val x = event.getX(idx)
                            val y = event.getY(idx)
                            val inside =
                                x >= 0f && y >= 0f && x < key.width && y < key.height
                            key.isPressed = inside
                            if (!inside) {
                                cancelKeyRunnable(key, longPressRunnable)
                            }
                        }
                        true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelKeyRunnable(key, longPressRunnable)
                    cancelKeyRunnable(key, repeatRunnable)
                    longPressFired = false
                    key.isPressed = false
                    pressScaleOff(key)
                    true
                }

                else -> true
            }
        }

        row.addView(key)
        return key
    }

    private fun addSpaceKey(row: LinearLayout, weight: Float) {
        val key = styleKey(Button(context), false)
        key.text = context.getString(R.string.space_label)
        key.textSize = 13f * sizeFactor()
        key.setTextColor(spaceLabelColor())
        key.gravity = Gravity.CENTER
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        keyRows[key] = row

        // Space commits on release so a long-press can't leak an extra space: a
        // normal tap inserts " " on ACTION_UP, while a 250ms hold instead opens the
        // language switcher and inserts nothing.
        var longPressFired = false
        var ownerPointerId = 0

        val toggleKeyboardRunnable = Runnable {
            longPressFired = true
            hapticStrong(key)
            onKeyLongPressed?.invoke(" ")
        }

        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ownerPointerId = event.getPointerId(0)
                    key.isPressed = true
                    pressScaleOn(key)
                    playSound(SOUND_SPACE)
                    longPressFired = false
                    cancelKeyRunnable(key, toggleKeyboardRunnable)
                    postKeyDelayed(
                        key,
                        toggleKeyboardRunnable,
                        LONG_PRESS_TIMEOUT
                    )
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val idx = event.findPointerIndex(ownerPointerId)
                    if (idx >= 0) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        val inside =
                            x >= 0f && y >= 0f && x < key.width && y < key.height
                        key.isPressed = inside
                        if (!inside) {
                            cancelKeyRunnable(key, toggleKeyboardRunnable)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    cancelKeyRunnable(key, toggleKeyboardRunnable)
                    if (!longPressFired) {
                        onKeyPressed?.invoke(" ")
                    }
                    key.isPressed = false
                    pressScaleOff(key)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelKeyRunnable(key, toggleKeyboardRunnable)
                    key.isPressed = false
                    pressScaleOff(key)
                    true
                }

                else -> true
            }
        }

        row.addView(key)
    }

    private fun addSpacer(row: LinearLayout, weight: Float) {
        val spacer = View(context)
        spacer.visibility = View.INVISIBLE
        spacer.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        )
        row.addView(spacer)
    }

    private fun styleKey(button: Button, isLetter: Boolean): Button {
        button.isAllCaps = false
        button.setPadding(0, 0, 0, 0)
        button.minHeight = 0
        button.minWidth = 0
        button.stateListAnimator = null
        button.gravity = Gravity.CENTER
        button.setIncludeFontPadding(false)
        button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        button.textSize = (if (isLetter) 18f else 15f) * sizeFactor()
        button.setTextColor(letterTextColor())
        button.background = resources.getDrawable(
            if (isLetter) {
                if (darkTheme) R.drawable.key_bg else R.drawable.key_bg_light
            } else {
                if (darkTheme) R.drawable.key_bg_action else R.drawable.key_bg_action_light
            },
            null
        )
        return button
    }

    private fun applyIcon(key: View, iconRes: Int, tint: Int) {
        val icon = (key as? FrameLayout)?.getChildAt(0) as? ImageView
        if (icon != null) {
            icon.setImageResource(iconRes)
            icon.imageTintList = ColorStateList.valueOf(tint)
        }
    }

    private fun styleShiftIcon() {
        shiftKey?.let { key ->
            applyIcon(
                key,
                if (isShiftLocked) R.drawable.ic_shift_locked else R.drawable.ic_shift,
                if (isShifted || isShiftLocked) accentColor() else iconColor()
            )
        }
    }

    /**
     * Reflects the target editor's IME action on the Enter key:
     * SEARCH -> magnifier, GO/NEXT/SEND -> right arrow, everything else (incl.
     * DONE and multiline) keeps the plain return arrow. Only the icon changes;
     * the "↵" event pipeline is untouched (RomanNepaliIME decides behavior).
     */
    fun setEnterAction(action: Int) {
        enterAction = action
        applyEnterActionIcon()
    }

    private fun applyEnterActionIcon() {
        val iconRes = when (enterAction and android.view.inputmethod.EditorInfo.IME_MASK_ACTION) {
            android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
            android.view.inputmethod.EditorInfo.IME_ACTION_GO -> R.drawable.ic_go
            android.view.inputmethod.EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_go
            android.view.inputmethod.EditorInfo.IME_ACTION_SEND -> R.drawable.ic_go
            else -> R.drawable.ic_enter
        }
        enterKey?.let { key ->
            applyIcon(key, iconRes, accentColor())
        }
    }

    private fun labelFor(entry: String): String {
        return if ((isShifted || isShiftLocked) && entry.length == 1 && entry[0] in 'a'..'z') {
            entry.uppercase()
        } else {
            entry
        }
    }

    private fun refreshKeyLabels() {
        letterKeys.forEach { (parts, entry) ->
            parts.main.text = labelFor(entry)
        }
        styleShiftIcon()
    }

    private fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        val isDoubleTap =
            now - lastShiftTapAt <= android.view.ViewConfiguration.getDoubleTapTimeout()
        lastShiftTapAt = now
        when {
            // Tapping while locked releases it (back to plain lowercase).
            isShiftLocked -> {
                isShiftLocked = false
                isShifted = false
            }
            // Second quick tap: caps lock, stays on across letters/symbols.
            isDoubleTap -> {
                isShiftLocked = true
                isShifted = true
            }
            else -> isShifted = !isShifted
        }
        refreshKeyLabels()
        styleShiftIcon()
        onShiftToggled?.invoke(isShifted)
    }

    /** External auto-capitalization (sentence start). Keeps the visual state in sync. */
    fun setShifted(shifted: Boolean) {
        isShifted = shifted
        refreshKeyLabels()
        styleShiftIcon()
    }

    /** Fired only when the USER taps the shift key (never for auto-capitalization). */
    var onShiftToggled: ((Boolean) -> Unit)? = null

    private fun toggleSymbols() {
        isSymbols = !isSymbols
        isShifted = false
        buildKeyboard()
    }

    fun rebuild() {
        post {
            buildKeyboard()
            requestLayout()
            invalidate()
        }
    }

    fun applyThemeIfChanged() {
        val preferred = Prefs.darkTheme(context)
        val size = Prefs.keyboardSize(context)
        if (preferred != darkTheme || size != lastAppliedSize) {
            darkTheme = preferred
            lastAppliedSize = size
            rebuild()
        }
    }

    private fun themeBackground(): Int =
        if (darkTheme) themedColor(R.color.keyboard_bg_dark) else themedColor(R.color.keyboard_bg_light)

    private fun letterTextColor(): Int =
        if (darkTheme) themedColor(R.color.letter_text_dark) else themedColor(R.color.letter_text_light)

    private fun secondaryTextColor(): Int =
        if (darkTheme) themedColor(R.color.secondary_text_dark) else themedColor(R.color.secondary_text_light)

    private fun iconColor(): Int =
        if (darkTheme) themedColor(R.color.icon_dark) else themedColor(R.color.icon_light)

    /** Marks the comma key as the Emoji entry point (hold to open) and makes the
     *  marker fully opaque while the Emoji pane is the active mode. Kept in the
     *  letter colour so it reads as part of the key, not as a red badge. */
    fun setEmojiMode(active: Boolean) {
        emojiModeActive = active
        styleCommaHint()
    }

    private fun styleCommaHint() {
        val hint = commaKeyHint ?: return
        val icon = resources.getDrawable(R.drawable.ic_emoji_hint, null)
        icon?.setTint(letterTextColor())
        icon?.alpha = if (emojiModeActive) 255 else 150
        hint.setCompoundDrawablesWithIntrinsicBounds(null, null, null, icon)
        hint.compoundDrawablePadding = dp(2)
    }

    private fun accentColor(): Int = themedColor(R.color.accent)

    private fun spaceLabelColor(): Int =
        if (darkTheme) themedColor(R.color.space_label_dark) else themedColor(R.color.space_label_light)

    private fun themedColor(resId: Int): Int = resources.getColor(resId, null)

    private fun dp(value: Int): Int =
        (resources.displayMetrics.density * value).toInt()
}