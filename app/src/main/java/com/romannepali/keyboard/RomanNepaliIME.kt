package com.romannepali.keyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import com.romannepali.keyboard.emoji.EmojiKeyboard
import com.romannepali.keyboard.suggestion.SuggestionEngine
import com.romannepali.keyboard.theme.ThemeManager
import android.view.ViewGroup
import android.os.Build
import android.view.WindowInsets
import android.view.WindowInsets.Type as InsetsType

class RomanNepaliIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var emojiKeyboard: EmojiKeyboard
    private lateinit var container: FrameLayout

    private val suggestionEngine = SuggestionEngine()
    private val themeManager by lazy { ThemeManager(applicationContext) }

    private val currentWord = StringBuilder()

    private var isShifted = true
    private var isSymbols = false
    private var isEmojiMode = false

    override fun onCreateInputView(): View {
        container = FrameLayout(this)

        emojiKeyboard = EmojiKeyboard(this)
        emojiKeyboard.onEmojiClickListener = { emoji ->
            currentInputConnection?.commitText(emoji, 1)
            showKeyboard()
        }
        container.addView(emojiKeyboard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val mainLayout = layoutInflater.inflate(R.layout.keyboard_main, null)
        suggestionBar = mainLayout.findViewById(R.id.suggestion_bar)
        suggestionBar.onSuggestionClickListener = { suggestion ->
            applySuggestion(suggestion)
        }

        keyboardView = mainLayout.findViewById(R.id.keyboard_view)
        keyboard = Keyboard(this, R.xml.keyboard_main)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        container.addView(mainLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        applyNavBarInsetPadding(mainLayout)
        applyTheme()
        showKeyboard()

        return container
    }

    private fun applyNavBarInsetPadding(view: View) {
        val root = view.rootView ?: return
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val navBarInset = insets.getInsets(InsetsType.navigationBars())
                view.setPadding(0, 0, 0, navBarInset.bottom)
            } else {
                @Suppress("DEPRECATION")
                val inset = insets.systemWindowInsetBottom
                view.setPadding(0, 0, 0, inset)
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun applyTheme() {
        themeManager.applyTheme(keyboardView)
    }

    private fun showKeyboard() {
        isEmojiMode = false
        emojiKeyboard.visibility = View.GONE
        container.getChildAt(1).visibility = View.VISIBLE
    }

    private fun showEmoji() {
        isEmojiMode = true
        emojiKeyboard.visibility = View.VISIBLE
        container.getChildAt(1).visibility = View.GONE
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentWord.clear()
        if (::suggestionBar.isInitialized) {
            suggestionBar.clearSuggestions()
        }
        isShifted = true
    }

    override fun onPress(primaryCode: Int) {
    }

    override fun onRelease(primaryCode: Int) {
    }

    private fun updateSuggestions() {
        if (currentWord.isNotEmpty()) {
            val suggestions = suggestionEngine.getSuggestions(currentWord.toString())
            suggestionBar.showSuggestions(suggestions)
        } else {
            suggestionBar.clearSuggestions()
        }
    }

    private fun applySuggestion(suggestion: String) {
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText(suggestion, 1)
        suggestionEngine.learnWord(suggestion)
        suggestionEngine.updateContext(suggestion)
        currentWord.clear()
        suggestionBar.clearSuggestions()
    }

    private fun commitPunctuation(ic: android.view.inputmethod.InputConnection, punctuation: String) {
        if (currentWord.isNotEmpty()) {
            suggestionEngine.learnWord(currentWord.toString())
            suggestionEngine.updateContext(currentWord.toString())
            currentWord.clear()
        }
        ic.commitText(punctuation, 1)
        suggestionBar.clearSuggestions()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        if (isEmojiMode) {
            showKeyboard()
        }

        when (primaryCode) {
            in 97..122 -> {
                val char = if (isShifted) {
                    isShifted = false
                    keyboardView.isShifted = false
                    primaryCode.toChar().uppercaseChar()
                } else {
                    primaryCode.toChar()
                }
                ic.commitText(char.toString(), 1)
                currentWord.append(char)
                updateSuggestions()
            }

            32 -> {
                if (currentWord.isNotEmpty()) {
                    suggestionEngine.learnWord(currentWord.toString())
                    suggestionEngine.updateContext(currentWord.toString())
                    currentWord.clear()
                }
                ic.commitText(" ", 1)
                suggestionBar.clearSuggestions()
            }

            -5 -> {
                if (currentWord.isNotEmpty()) {
                    currentWord.deleteCharAt(currentWord.length - 1)
                    updateSuggestions()
                }
                ic.deleteSurroundingText(1, 0)
            }

            -4 -> {
                val editorInfo = currentInputEditorInfo
                val action = editorInfo?.imeOptions
                    ?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_UNSPECIFIED

                when (action) {
                    EditorInfo.IME_ACTION_SEND -> ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                    EditorInfo.IME_ACTION_GO -> ic.performEditorAction(EditorInfo.IME_ACTION_GO)
                    EditorInfo.IME_ACTION_SEARCH -> ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                    EditorInfo.IME_ACTION_DONE -> ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
                    else -> {
                        if (currentWord.isNotEmpty()) {
                            suggestionEngine.learnWord(currentWord.toString())
                            currentWord.clear()
                        }
                        ic.commitText("\n", 1)
                    }
                }
                suggestionBar.clearSuggestions()
            }

            -1 -> {
                isShifted = !isShifted
                keyboardView.isShifted = isShifted
            }

            -2 -> {
                isSymbols = !isSymbols
                if (isSymbols) {
                    keyboard = Keyboard(this, R.xml.keyboard_symbols)
                } else {
                    keyboard = Keyboard(this, R.xml.keyboard_main)
                }
                keyboardView.keyboard = keyboard
                applyTheme()
            }

            -3 -> {
                showEmoji()
            }

            46 -> commitPunctuation(ic, ".")
            44 -> commitPunctuation(ic, ",")
            else -> {
                ic.commitText(primaryCode.toChar().toString(), 1)
            }
        }
    }

    override fun onText(text: CharSequence?) {
        text?.let {
            currentInputConnection?.commitText(it, 1)
        }
    }

    override fun swipeLeft() {
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
            currentWord.clear()
            suggestionBar.clearSuggestions()
        }
    }

    override fun swipeRight() {
    }

    override fun swipeDown() {
    }

    override fun swipeUp() {
    }
}
