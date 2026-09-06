package com.merotype.keyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.merotype.keyboard.R
import com.merotype.keyboard.data.LocalLanguageDatabase
import com.merotype.keyboard.engine.NormalizationEngine
import kotlinx.coroutines.launch

/**
 * Main keyboard service for MeroType Roman Nepali keyboard
 */
class MeroTypeKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var suggestionContainer: LinearLayout
    private lateinit var database: LocalLanguageDatabase
    private lateinit var normalization: NormalizationEngine

    private var shiftActive = false
    private var capsLockActive = false
    private val textBuffer = StringBuilder()
    private val typedWords = mutableListOf<String>()

    override fun onCreateInputView(): View {
        // Initialize components
        database = LocalLanguageDatabase.getInstance(this)
        normalization = NormalizationEngine()

        // Create main container
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Create keyboard view
        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        keyboardView = KeyboardView(this).apply {
            keyboard = Keyboard(this@MeroTypeKeyboardService, R.xml.keyboard_layout)
            isPreviewEnabled = true
            setOnKeyboardActionListener(this@MeroTypeKeyboardService)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                6f
            )
        }
        keyboardContainer.addView(keyboardView)

        // Create suggestion bar
        suggestionContainer = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                80
            )
        }.let { scroll ->
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                scroll.addView(this)
                this
            }
        }
        keyboardContainer.addView(suggestionContainer)

        container.addView(keyboardContainer)
        return container
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> onKeyDelete(inputConnection)
            Keyboard.KEYCODE_SHIFT -> onKeyShift(inputConnection)
            32 -> onKeySpace(inputConnection)  // Space
            10 -> onKeyEnter(inputConnection)  // Enter
            else -> {
                if (primaryCode in 32..126) {
                    onKeyLetter(primaryCode.toChar(), inputConnection)
                }
            }
        }

        updateSuggestions(inputConnection)
    }

    private fun onKeyLetter(char: Char, inputConnection: InputConnection) {
        var letter = char

        if (capsLockActive || shiftActive) {
            letter = letter.uppercaseChar()
            if (shiftActive) {
                shiftActive = false
            }
        }

        textBuffer.append(letter)
        inputConnection.commitText(letter.toString(), 1)
    }

    private fun onKeyDelete(inputConnection: InputConnection) {
        if (textBuffer.isNotEmpty()) {
            textBuffer.deleteCharAt(textBuffer.length - 1)
            inputConnection.deleteSurroundingText(1, 0)
        }
    }

    private fun onKeyShift(inputConnection: InputConnection) {
        if (shiftActive) {
            capsLockActive = !capsLockActive
            shiftActive = false
        } else {
            shiftActive = true
        }
    }

    private fun onKeySpace(inputConnection: InputConnection) {
        val currentWord = textBuffer.toString()
        if (currentWord.isNotEmpty()) {
            typedWords.add(currentWord)
            textBuffer.clear()
        }
        inputConnection.commitText(" ", 1)
    }

    private fun onKeyEnter(inputConnection: InputConnection) {
        inputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        )
        inputConnection.sendKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
        )
        typedWords.clear()
        textBuffer.clear()
    }

    private fun updateSuggestions(inputConnection: InputConnection) {
        lifecycleScope.launch {
            val currentWord = textBuffer.toString()
            if (currentWord.length < 1) {
                suggestionContainer.removeAllViews()
                return@launch
            }

            try {
                val words = database.wordDao().findByPrefix(
                    currentWord.lowercase(),
                    limit = 5
                )

                displaySuggestions(words.map { it.text }, inputConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun displaySuggestions(
        suggestions: List<String>,
        inputConnection: InputConnection
    ) {
        suggestionContainer.removeAllViews()

        for (suggestion in suggestions) {
            val button = Button(this).apply {
                text = suggestion
                layoutParams = LinearLayout.LayoutParams(
                    200,
                    80
                ).apply {
                    setMargins(4, 4, 4, 4)
                }
                setOnClickListener {
                    onSuggestionSelected(suggestion, inputConnection)
                }
            }
            suggestionContainer.addView(button)
        }
    }

    private fun onSuggestionSelected(
        word: String,
        inputConnection: InputConnection
    ) {
        val currentWord = textBuffer.toString()

        // Delete current word
        for (i in 0 until currentWord.length) {
            inputConnection.deleteSurroundingText(1, 0)
        }

        // Insert selected word
        inputConnection.commitText(word, 1)

        textBuffer.clear()
        typedWords.add(word)
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
