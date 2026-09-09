package com.romannepali.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
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

    private val lettersRows: List<List<String?>> = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf(null, "a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf(SHIFT, "z", "x", "c", "v", "b", "n", "m", BACKSPACE),
        listOf(SYMBOLS, SPACE, DOT, ENTER)
    )

    private val symbolRows: List<List<String?>> = listOf(
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
        listOf("-", "_", "=", "+", "[", "]", "{", "}", "\\", "|"),
        listOf(";", ":", "'", "\"", ",", "<", ".", ">", "/", "?"),
        listOf(SYMBOLS, SPACE, DOT, ENTER)
    )

    private val shiftWeight = 1.5f
    private val backspaceWeight = 1.5f
    private val spaceWeight = 4f
    private val symbolsWeight = 2f
    private val dotWeight = 1.2f
    private val enterWeight = 2f

    // Remember letter keys so we can re-render upper/lower case without a full rebuild.
    private val letterButtons = mutableListOf<Pair<Button, String>>()

    var isShifted = false
        private set

    private var isSymbols = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(28, 27, 31))
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
                if (entry != null) {
                    addEntry(row, entry)
                } else {
                    addSpacer(row)
                }
            }

            addView(row)
        }

        refreshKeyLabels()
    }

    private fun addEntry(row: LinearLayout, entry: String) {
        when (entry) {
            SHIFT -> addActionKey(row, "⇧", shiftWeight, click = { toggleShift() })
            BACKSPACE -> addActionKey(
                row,
                "⌫",
                backspaceWeight,
                click = { onKeyPressed?.invoke("⌫") },
                repeat = true
            )
            SPACE -> addActionKey(
                row,
                " ",
                spaceWeight,
                click = { onKeyPressed?.invoke(" ") },
                onLongPress = { onKeyLongPressed?.invoke(" ") }
            )
            SYMBOLS -> addActionKey(
                row,
                if (isSymbols) "ABC" else "?123",
                symbolsWeight,
                click = { toggleSymbols() }
            )
            DOT -> addActionKey(row, ".", dotWeight, click = { onKeyPressed?.invoke(".") })
            ENTER -> addActionKey(row, "↵", enterWeight, click = { onKeyPressed?.invoke("↵") })

            else -> {
                val isLetter = entry.length == 1 && entry[0] in 'a'..'z'
                addKey(row, labelFor(entry), 1f, isLetter) {
                    val sent = if (isLetter && isShifted) entry.uppercase() else entry
                    onKeyPressed?.invoke(sent)
                    if (isLetter && isShifted) {
                        isShifted = false
                        refreshKeyLabels()
                    }
                }.also { key ->
                    if (isLetter) {
                        letterButtons.add(key to entry)
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
        key.setOnClickListener { onClick() }
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
            click()
        }

        row.addView(key)
    }

    private fun addSpacer(row: LinearLayout) {
        val spacer = View(context)
        spacer.visibility = View.INVISIBLE
        spacer.layoutParams = LinearLayout.LayoutParams(
            0,
            LayoutParams.MATCH_PARENT,
            1f
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