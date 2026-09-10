package com.romannepali.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.LinearLayout

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

    // Remember letter keys so we can re-render upper/lower case without a full rebuild.
    private val letterButtons = mutableListOf<Pair<Button, String>>()

    var isShifted = false
        private set

    private var isSymbols = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.parseColor("#121212"))
        buildKeyboard()
    }

    var onKeyPressed: ((String) -> Unit)? = null
    var onKeyLongPressed: ((String) -> Unit)? = null

    private fun buildKeyboard() {
        removeAllViews()
        letterButtons.clear()

        val rows = if (isSymbols) symbolRows else lettersRows

        rows.forEach { rowEntries ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
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

        refreshKeyLabels()
    }

    private fun addEntry(row: LinearLayout, entry: KeyDef) {
        when (entry.label) {
            SHIFT -> addActionKey(row, "⇧", entry.weight, click = { toggleShift() })
            BACKSPACE -> addActionKey(
                row,
                "⌫",
                entry.weight,
                click = { onKeyPressed?.invoke("⌫") },
                repeat = true
            )
            SPACE -> addActionKey(
                row,
                " ",
                entry.weight,
                click = { onKeyPressed?.invoke(" ") },
                onLongPress = { onKeyLongPressed?.invoke(" ") }
            )
            SYMBOLS -> addActionKey(
                row,
                if (isSymbols) "ABC" else "?123",
                entry.weight,
                click = { toggleSymbols() }
            )
            COMMA -> addActionKey(row, ",", entry.weight, click = { onKeyPressed?.invoke(",") })
            DOT -> addActionKey(row, ".", entry.weight, click = { onKeyPressed?.invoke(".") })
            ENTER -> addActionKey(row, "→", entry.weight, click = { onKeyPressed?.invoke("↵") })

            else -> {
                val text = entry.label ?: return
                val isLetter = text.length == 1 && text[0] in 'a'..'z'
                addKey(row, labelFor(text), entry.weight, isLetter) {
                    val sent = if (isLetter && isShifted) text.uppercase() else text
                    onKeyPressed?.invoke(sent)
                    if (isLetter && isShifted) {
                        isShifted = false
                        refreshKeyLabels()
                    }
                }.also { key ->
                    if (isLetter) {
                        letterButtons.add(key to text)
                    }
                }
            }
        }
    }

    private fun addKey(
        row: LinearLayout,
        label: String,
        weight: Float,
        isLetter: Boolean,
        onClick: () -> Unit
    ): Button {
        val key = styleKey(Button(context), isLetter)
        key.text = label
        key.setOnClickListener {
            key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        }
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(3, 3, 3, 3)
        }
        row.addView(key)
        return key
    }

    private fun addActionKey(
        row: LinearLayout,
        label: String,
        weight: Float,
        click: () -> Unit,
        onLongPress: (() -> Unit)? = null,
        repeat: Boolean = false
    ) {
        val key = styleKey(Button(context), false)
        key.text = label
        key.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            weight
        ).apply {
            setMargins(3, 3, 3, 3)
        }

        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        var longPressFired = false

        val repeatRunnable = object : Runnable {
            override fun run() {
                if (longPressFired) {
                    key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    click()
                    key.postDelayed(this, 40)
                }
            }
        }

        val longPressRunnable = Runnable {
            longPressFired = true
            if (repeat) {
                click()
                key.postDelayed(repeatRunnable, 40)
            } else {
                onLongPress?.invoke()
            }
        }

        key.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longPressFired = false
                    key.postDelayed(longPressRunnable, longPressTimeout)
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (longPressFired) {
                        true
                    } else {
                        val rect = Rect()
                        key.getGlobalVisibleRect(rect)
                        if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                            key.removeCallbacks(longPressRunnable)
                        }
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    key.removeCallbacks(longPressRunnable)
                    key.removeCallbacks(repeatRunnable)
                    if (longPressFired) {
                        longPressFired = false
                        true // consume: prevents the click firing after a long-press
                    } else {
                        false
                    }
                }

                else -> false
            }
        }

        key.setOnClickListener {
            // Long-press consumes the up event, so this normally never fires then.
            if (longPressFired) {
                longPressFired = false
                return@setOnClickListener
            }
            key.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            click()
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
        button.textSize = if (isLetter) 18f else 15f
        button.setTextColor(Color.WHITE)
        button.background = resources.getDrawable(
            if (isLetter) R.drawable.key_bg else R.drawable.key_bg_action,
            null
        )
        return button
    }

    private fun labelFor(entry: String): String {
        return if (isShifted && entry.length == 1 && entry[0] in 'a'..'z') {
            entry.uppercase()
        } else {
            entry
        }
    }

    private fun refreshKeyLabels() {
        letterButtons.forEach { (button, entry) ->
            button.text = labelFor(entry)
        }
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
}