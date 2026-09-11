package com.romannepali.keyboard

import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.romannepali.keyboard.suggestion.SuggestionEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RomanNepaliIME : InputMethodService() {

    private var keyboardView: GboardKeyboardView? = null
    private var suggestionBar: SuggestionBar? = null
    private var mainView: View? = null

    private lateinit var suggestionEngine: SuggestionEngine
    private val currentWord = StringBuilder()

    // Deleted text remembered for the undo arrow.
    private var deletedText: String? = null

    // Suggestions are computed off the UI thread so typing never stutters.
    private val suggestionExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var suggestionJob = 0L

    override fun onCreate() {
        super.onCreate()

        suggestionEngine = SuggestionEngine(this)

        updateFullscreenMode()
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onCreateInputView(): View {
        return createKeyboard()
    }

    private fun createKeyboard(): View {
        val view = layoutInflater.inflate(
            R.layout.keyboard_main,
            null
        )

        keyboardView = view.findViewById(R.id.keyboard_view)
        suggestionBar = view.findViewById(R.id.suggestion_bar)
        mainView = view

        val dark = Prefs.darkTheme(this)
        val bg = if (dark) Color.parseColor("#121212") else Color.parseColor("#E7EAEE")
        view.setBackgroundColor(bg)
        view.findViewById<View>(R.id.suggestion_row).setBackgroundColor(bg)

        // Keep the keyboard above system navigation/gesture bars in all orientations.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        suggestionBar?.onSuggestionClickListener = { suggestion ->
            applySuggestion(suggestion)
        }

        suggestionBar?.onUndoClickListener = {
            undoDelete()
        }

        keyboardView?.onKeyPressed = { text ->
            val ic = currentInputConnection

            if (ic != null) {
                when (text) {
                    " " -> {
                        finishCurrentWord()
                        ic.commitText(" ", 1)
                        suggestionBar?.clearSuggestions()
                    }

                    "." -> {
                        finishCurrentWord()
                        ic.commitText(".", 1)
                        suggestionBar?.clearSuggestions()
                    }

                    "," -> {
                        finishCurrentWord()
                        ic.commitText(",", 1)
                        suggestionBar?.clearSuggestions()
                    }

                    "↵" -> {
                        finishCurrentWord()
                        ic.commitText("\n", 1)
                        suggestionBar?.clearSuggestions()
                    }

                    "⌫" -> {
                        val restored = deletedText
                        val deleted = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                        deletedText = if (deleted == restored) restored + deleted else deleted

                        if (currentWord.isNotEmpty()) {
                            currentWord.deleteCharAt(currentWord.length - 1)
                            updateSuggestions()
                        } else {
                            suggestionBar?.clearSuggestions()
                        }

                        ic.deleteSurroundingText(1, 0)
                        suggestionBar?.setUndoVisible(deletedText?.isNotEmpty() == true)
                    }

                    "⇧" -> {
                        // Shift is handled inside GboardKeyboardView.
                    }

                    else -> {
                        val isShiftedText =
                            text.length == 1 && text[0].isUpperCase()

                        if (isShiftedText && currentWord.isEmpty()) {
                            ic.commitText(text, 1)
                        } else {
                            ic.commitText(text, 1)
                            currentWord.append(text)
                            updateSuggestions()
                        }
                    }
                }
            }
        }

        // Long-press space opens the system keyboard switcher (like Gboard).
        keyboardView?.onKeyLongPressed = { text ->
            if (text == " ") {
                val imm = getSystemService(INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        return view
    }

    private fun updateSuggestions() {
        if (!Prefs.suggestions(this)) {
            mainHandler.post { suggestionBar?.clearSuggestions() }
            return
        }

        val word = currentWord.toString()
        if (word.isEmpty()) {
            mainHandler.post { suggestionBar?.clearSuggestions() }
            return
        }

        val job = ++suggestionJob
        suggestionExecutor.execute {
            val suggestions = suggestionEngine.getSuggestions(word)
            mainHandler.post {
                // Only apply if no newer keystroke superseded this one.
                if (job == suggestionJob) {
                    suggestionBar?.showSuggestions(suggestions)
                }
            }
        }
    }

    private fun finishCurrentWord() {
        if (currentWord.isNotEmpty()) {
            suggestionEngine.learnWord(currentWord.toString())
            suggestionEngine.updateContext(currentWord.toString())
            currentWord.clear()
        }
    }

    private fun applySuggestion(suggestion: String) {
        val ic = currentInputConnection ?: return

        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }

        ic.commitText(suggestion, 1)

        val words = suggestion.split(Regex("\\s+")).filter { it.isNotBlank() }
        words.forEach { word ->
            suggestionEngine.learnWord(word)
            suggestionEngine.updateContext(word)
        }

        currentWord.clear()
        suggestionBar?.clearSuggestions()
    }

    private fun undoDelete() {
        val ic = currentInputConnection ?: return
        val restored = deletedText ?: return

        ic.commitText(restored, 1)

        if (restored.all { it.isLetter() }) {
            currentWord.append(restored)
            updateSuggestions()
        } else {
            finishCurrentWord()
            suggestionBar?.clearSuggestions()
        }

        deletedText = null
        suggestionBar?.setUndoVisible(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateFullscreenMode()

        keyboardView?.rebuild()
        mainView?.requestApplyInsets()
    }

    override fun onStartInput(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInput(attribute, restarting)

        // Invalidate any in-flight suggestion computation.
        suggestionJob++
        currentWord.clear()
        deletedText = null
        suggestionBar?.setUndoVisible(false)
        suggestionBar?.clearSuggestions()
        keyboardView?.applyThemeIfChanged()
    }

    override fun onDestroy() {
        suggestionJob++
        suggestionExecutor.shutdownNow()
        keyboardView?.onKeyPressed = null
        keyboardView = null
        suggestionBar = null

        super.onDestroy()
    }
}
