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

    // Number row sits slightly shorter than letter rows (Gboard style).
    private val NUMBER_ROW_WEIGHT = 0.92f

    // ---- Production layout spec (dp) ----
    private val SIDE_MARGIN_MIN = 4
    private val SIDE_MARGIN_DEFAULT = 6
    private val KEY_MIN = 30
    private val KEY_MAX = 55
    private val KEY_EDGE = 2          // per-side margin -> 4dp inter-key gap
    private val LETTER_ROW = 48       // key height band 40-52dp
    private val LETTER_ROW_LANDSCAPE = 44
    private val CHIN = 20             // transparent safety chin for nav gestures

    // Tactile spec: 250ms long-press, tap vs 40ms hold-and-repeat for backspace.
    private val LONG_PRESS_TIMEOUT = 250L
    private val REPEAT_INTERVAL = 40L

    // Triple-pitch audio: letter (high), space (mid), backspace/enter (low).
    private val SOUND_LETTER = 0
    private val SOUND_SPACE = 1
    private val SOUND_DELETE = 2

    var isShifted = false
        private set

    private var isSymbols = false
    private var darkTheme = true
    private var numberRowEnabled = Prefs.numberRow(context)

    private var soundPool: SoundPool? = null
    private var soundLetter = 0
    private var soundSpace = 0
    private var soundDelete = 0

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        darkTheme = Prefs.darkTheme(context)
        buildKeyboard()
    }

    var onKeyPressed: ((String) -> Unit)? = null
    var onKeyLongPressed: ((String) -> Unit)? = null

    // ------------------------------------------------------------------
    // Sound: 3 short synthesized clicks (high / mid / low pitch).
    // ------------------------------------------------------------------

    private fun ensureSound() {
        if (soundPool != null) return
        soundPool = SoundPool.Builder().setMaxStreams(3).build()
        soundLetter = soundPool!!.load(context, R.raw.key_click_letter, 1)
        soundSpace = soundPool!!.load(context, R.raw.key_click_space, 1)
        soundDelete = soundPool!!.load(context, R.raw.key_click_delete, 1)
    }

    private fun playSound(kind: Int) {
        if (!Prefs.sound(context)) return
        ensureSound()
        val stream = when (kind) {
            SOUND_SPACE -> soundSpace
            SOUND_DELETE -> soundDelete
            else -> soundLetter
        }
        soundPool?.play(stream, 0.8f, 0.8f, 1, 0, 1f)
    }

    // Haptics: crisp impulse for letters, longer/stronger for structural keys.
    private fun hapticTap(view: View) {
        if (Prefs.vibration(context)) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

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

    // ------------------------------------------------------------------
    // Keyboard construction
    // ------------------------------------------------------------------

    private fun buildKeyboard() {
        removeAllViews()
        letterKeys.clear()
        keyRows.clear()
        setBackgroundColor(themeBackground())
        // Outer horizontal inset computed from the real screen width so a weight-1
        // key lands inside [KEY_MIN, KEY_MAX] dp; any slack becomes the side margin.
        val sideMargin = keyboardSideMargin()
        setPadding(sideMargin, 0, sideMargin, 0)

        val rows = if (isSymbols) symbolRows else lettersRows

        // Optional top number row (off by default; toggled in settings).
        if (numberRowEnabled && !isSymbols) {
            val digitRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    0,
                    NUMBER_ROW_WEIGHT
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
            LayoutParams(LayoutParams.MATCH_PARENT, dp(CHIN))
        )

        refreshKeyLabels()

        // Keep keys at full row height: grow the keyboard when a 5th row is shown.
        layoutParams?.let { lp ->
            lp.height = keyboardHeightPx()
            layoutParams = lp
        }
    }

    private fun keyboardHeightPx(): Int {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val letterRow = if (landscape) dp(LETTER_ROW_LANDSCAPE) else dp(LETTER_ROW)
        val withNumber = numberRowEnabled && !isSymbols
        val totalWeight = 4f + if (withNumber) NUMBER_ROW_WEIGHT else 0f
        return (letterRow * totalWeight).toInt() + dp(CHIN)
    }

    /**
     * Computes the keyboard's horizontal inset from the actual screen width.
     *
     * Key width = (available width - total gaps) / keys in row, clamped to
     * [KEY_MIN, KEY_MAX]. If the natural width falls outside that band, the
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
        val unit = visible.coerceIn(dp(KEY_MIN), dp(KEY_MAX))
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
            COMMA -> addActionKey(
                row,
                entry.weight,
                label = ",",
                hEnd = 8, // safety gap before the space bar
                click = { onKeyPressed?.invoke(",") }
            )
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
            )

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
                        val sent = if (isLetter && isShifted) text.uppercase() else text
                        onKeyPressed?.invoke(sent)
                        if (isLetter && isShifted) {
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
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(dp(KEY_EDGE), dp(3), dp(KEY_EDGE), dp(3))
        }
        keyRows[key] = row

        val main = TextView(context).apply {
            text = label
            textSize = if (isLetter) 18f else 17f
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
                textSize = 10f
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
            textSize = 20f
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
        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    key.isPressed = true
                    pressScaleOn(key)
                    hapticTap(key)
                    playSound(SOUND_LETTER)
                    onPress()
                    showPopup(popup, main.text, key)
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        longPressFired = false
                        key.removeCallbacks(longPressRunnable)
                        key.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val rect = Rect()
                    key.getGlobalVisibleRect(rect)
                    val inside = rect.contains(event.rawX.toInt(), event.rawY.toInt())
                    key.isPressed = inside
                    if (!inside) {
                        key.removeCallbacks(longPressRunnable)
                        hidePopup(popup, key)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    key.removeCallbacks(longPressRunnable)
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
        repeat: Boolean = false,
        strong: Boolean = false,
        sound: Int = SOUND_LETTER,
        hStart: Int = 2,
        hEnd: Int = 2
    ) {
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
            setMargins(dp(hStart), dp(3), dp(hEnd), dp(3))
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
                    hapticTap(key)
                    playSound(sound)
                    click()
                    key.postDelayed(this, REPEAT_INTERVAL)
                }
            }
        }

        val longPressRunnable = Runnable {
            longPressFired = true
            if (repeat) {
                click()
                key.postDelayed(repeatRunnable, REPEAT_INTERVAL)
            }
        }

        // Action keys also commit on touch-down for instant response. Backspace does
        // a first delete on down, then starts repeating once the long-press fires.
        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    key.isPressed = true
                    pressScaleOn(key)
                    if (strong) hapticStrong(key) else hapticTap(key)
                    playSound(sound)
                    longPressFired = false
                    click()
                    if (repeat && event.actionMasked == MotionEvent.ACTION_DOWN) {
                        key.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (longPressFired) {
                        true
                    } else {
                        val rect = Rect()
                        key.getGlobalVisibleRect(rect)
                        val inside = rect.contains(event.rawX.toInt(), event.rawY.toInt())
                        key.isPressed = inside
                        if (!inside) {
                            key.removeCallbacks(longPressRunnable)
                        }
                        true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    key.removeCallbacks(longPressRunnable)
                    key.removeCallbacks(repeatRunnable)
                    longPressFired = false
                    key.isPressed = false
                    pressScaleOff(key)
                    true
                }

                else -> true
            }
        }

        row.addView(key)
    }

    private fun addSpaceKey(row: LinearLayout, weight: Float) {
        val key = styleKey(Button(context), false)
        key.text = context.getString(R.string.space_label)
        key.textSize = 13f
        key.setTextColor(spaceLabelColor())
        key.gravity = Gravity.CENTER
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(dp(2), dp(3), dp(2), dp(3))
        }
        keyRows[key] = row

        val toggleKeyboardRunnable = Runnable {
            hapticStrong(key)
            onKeyLongPressed?.invoke(" ")
        }

        // Space commits on touch-down too: the old click-on-lift could be cancelled
        // by finger drift / a fast second tap, merging words ("friday also" -> "fridalso").
        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    key.isPressed = true
                    pressScaleOn(key)
                    hapticStrong(key)
                    playSound(SOUND_SPACE)
                    onKeyPressed?.invoke(" ")
                    // Only the initial finger can arm the long-press keyboard switcher.
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        key.postDelayed(toggleKeyboardRunnable, LONG_PRESS_TIMEOUT)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val rect = Rect()
                    key.getGlobalVisibleRect(rect)
                    val inside = rect.contains(event.rawX.toInt(), event.rawY.toInt())
                    key.isPressed = inside
                    if (!inside) {
                        key.removeCallbacks(toggleKeyboardRunnable)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    key.removeCallbacks(toggleKeyboardRunnable)
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
        button.textSize = if (isLetter) 18f else 15f
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
                R.drawable.ic_shift,
                if (isShifted) accentColor() else iconColor()
            )
        }
    }

    private fun labelFor(entry: String): String {
        return if (isShifted && entry.length == 1 && entry[0] in 'a'..'z') {
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
        isShifted = !isShifted
        refreshKeyLabels()
    }

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
        val preferredNumberRow = Prefs.numberRow(context)
        if (preferred != darkTheme || preferredNumberRow != numberRowEnabled) {
            darkTheme = preferred
            numberRowEnabled = preferredNumberRow
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

    private fun accentColor(): Int = themedColor(R.color.accent)

    private fun spaceLabelColor(): Int =
        if (darkTheme) themedColor(R.color.space_label_dark) else themedColor(R.color.space_label_light)

    private fun themedColor(resId: Int): Int = resources.getColor(resId, null)

    private fun dp(value: Int): Int =
        (resources.displayMetrics.density * value).toInt()
}