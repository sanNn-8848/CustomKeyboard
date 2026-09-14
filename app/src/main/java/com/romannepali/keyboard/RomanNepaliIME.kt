package com.romannepali.keyboard

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.romannepali.keyboard.suggestion.PredictionContext
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

    // Last finished words (most recent last) fed back into prediction.
    private val contextWords = mutableListOf<String>()
    private val maxContextSize = 3

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

        applyKeyboardTheme()

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
                    " " -> handleSpace(ic)

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
                    }

                    "⇧" -> {
                        // Shift is handled inside GboardKeyboardView.
                    }

                    else -> {
                        val shifted = text.length == 1 && text[0].isUpperCase()
                        val sentenceStart =
                            text.length == 1 && text[0].isLowerCase() && isSentenceStart()
                        val sent = if (shifted || sentenceStart) text.uppercase() else text

                        ic.commitText(sent, 1)
                        if (sent.length == 1 && sent[0].isLetter()) {
                            currentWord.append(sent.lowercase())
                            updateSuggestions()
                        }
                    }
                }

                updateUndoUi()
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

    /** FrostGlass backdrop: gradient glass + static coral glow + mountain silhouette. */
    private fun applyKeyboardTheme() {
        val dark = Prefs.darkTheme(this)
        mainView?.setBackgroundResource(
            if (dark) R.drawable.bg_keyboard_container_dark else R.drawable.bg_keyboard_container_light
        )
        mainView?.findViewById<android.widget.ImageView>(R.id.frost_glow)?.setImageResource(
            if (dark) R.drawable.bg_glow_dark else R.drawable.bg_glow_light
        )
        mainView?.findViewById<android.widget.ImageView>(R.id.frost_mountains)?.setImageResource(
            if (dark) R.drawable.bg_mountains_dark else R.drawable.bg_mountains_light
        )
        suggestionBar?.applyTheme(dark)
    }

    /** Steps taken when the user presses space (double-space -> period, autocorrect). */
    private fun handleSpace(ic: android.view.inputmethod.InputConnection) {
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""

        if (before == " ") {
            // Double space -> ". ".
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            finishCurrentWord()
        } else {
            finishCurrentWord(autocorrect = true)
            ic.commitText(" ", 1)
        }
        suggestionBar?.clearSuggestions()
    }

    /**
     * Closes the current word: learns it, feeds context, and (optionally)
     * autocorrects it conservatively before the terminator is inserted.
     */
    private fun finishCurrentWord(autocorrect: Boolean = false) {
        if (currentWord.isEmpty()) return
        val ic = currentInputConnection ?: return

        val raw = currentWord.toString()
        val word = raw.lowercase()

        var committed = word
        if (autocorrect) {
            val correction = suggestionEngine.getAutoCorrection(word, contextWords)
            if (correction.isCorrection && correction.confidence >= AUTO_CORRECT_THRESHOLD) {
                ic.deleteSurroundingText(raw.length, 0)
                ic.commitText(correction.word, 1)
                committed = correction.word
            }
        }

        suggestionEngine.learnWord(committed)
        suggestionEngine.updateContext(committed)
        pushContext(committed)
        currentWord.clear()
    }

    private fun pushContext(word: String) {
        if (word.isBlank()) return
        contextWords.add(word)
        if (contextWords.size > maxContextSize) {
            contextWords.removeAt(0)
        }
    }

    /** True when the caret sits at the start of a fresh sentence. */
    private fun isSentenceStart(): Boolean {
        val ic = currentInputConnection ?: return true
        val before = ic.getTextBeforeCursor(4, 0)?.toString() ?: return true
        val trimmed = before.trimEnd(' ')
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        return last == '.' || last == '!' || last == '?' || last == '\n'
    }

    private fun currentPredictionContext(): PredictionContext = PredictionContext(
        currentWord = currentWord.toString(),
        previousWords = contextWords.toList(),
        sentenceStart = isSentenceStart(),
        cursorPosition = currentWord.length
    )

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
            val context = currentPredictionContext()
            val suggestions = suggestionEngine.predict(context, limit = 3).map { it.word }
            mainHandler.post {
                // Only apply if no newer keystroke superseded this one.
                if (job == suggestionJob) {
                    suggestionBar?.showSuggestions(suggestions)
                }
            }
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
            pushContext(word)
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
        updateUndoUi()
    }

    private fun updateUndoUi() {
        val available = deletedText?.isNotEmpty() == true
        val centered = available && currentWord.isEmpty()
        suggestionBar?.setUndoState(available, centered)
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
        contextWords.clear()
        deletedText = null
        suggestionBar?.clearSuggestions()
        updateUndoUi()
        keyboardView?.applyThemeIfChanged()
        applyKeyboardTheme()
    }

    override fun onDestroy() {
        suggestionJob++
        suggestionExecutor.shutdownNow()
        keyboardView?.onKeyPressed = null
        keyboardView = null
        suggestionBar = null

        super.onDestroy()
    }

    private companion object {
        /** Conservative: only autocorrect when the model is fairly confident. */
        const val AUTO_CORRECT_THRESHOLD = 0.45f
    }
}