package com.romannepali.keyboard

import android.content.res.Configuration
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.romannepali.keyboard.clipboard.ClipboardManager
import com.romannepali.keyboard.clipboard.ClipboardPane
import com.romannepali.keyboard.emoji.EmojiPane
import com.romannepali.keyboard.suggestion.PredictionContext
import com.romannepali.keyboard.suggestion.SuggestionEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RomanNepaliIME : InputMethodService() {

    private var keyboardView: GboardKeyboardView? = null
    private var suggestionBar: SuggestionBar? = null
    private var dragOverlay: SuggestionDragOverlay? = null
    private var featureCarousel: FeatureCarouselView? = null
    private var featurePane: android.widget.FrameLayout? = null
    private var clipboardPane: ClipboardPane? = null
    private var emojiPane: EmojiPane? = null
    private var mainView: View? = null

    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var clipboardManager: ClipboardManager
    private val currentWord = StringBuilder()

    // Deleted text remembered for the undo arrow.
    private var deletedText: String? = null
    // Last suppressed word for the undo arrow (reversible without destroying history).
    private var lastSuppressedWord: String? = null
    // Active feature pane index: 0=suggestions, 1=clipboard, 2=emoji.
    private var currentMode = 0

    // Last finished words (most recent last) fed back into prediction.
    private val contextWords = mutableListOf<String>()
    private val maxContextSize = 3

    // Suggestions are computed off the UI thread so typing never stutters.
    private val suggestionExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var suggestionJob = 0L

    private var systemClipboardListener:
        android.content.ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate() {
        super.onCreate()

        suggestionEngine = SuggestionEngine(this)
        clipboardManager = ClipboardManager(this)

        // Watch the system clipboard so copies made in other apps (links, cut text,
        // etc.) land in our clipboard pane. Android 10+ only allows this when this
        // keyboard is the device's DEFAULT keyboard; otherwise we simply stay silent.
        systemClipboardListener =
            android.content.ClipboardManager.OnPrimaryClipChangedListener {
                mainHandler.post { captureSystemClipboard() }
            }
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
            cm?.addPrimaryClipChangedListener(systemClipboardListener!!)
        } catch (_: SecurityException) {
            // Not the default IME — clipboard is off-limits.
        }

        updateFullscreenMode()
    }

    /**
     * Best-effort copy of whatever the user copied/cut in another app into our
     * clipboard history. Never throws: silently no-ops when the platform forbids us.
     */
    private fun captureSystemClipboard() {
        if (isPasswordField()) return
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
                ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isNotBlank()) {
                clipboardManager.copy(text)
            }
        } catch (_: SecurityException) {
            // Android 10+ and we are not the default IME.
        } catch (_: IllegalStateException) {
            // Primary clip not yet ready.
        }
    }

    private fun isPasswordField(): Boolean {
        val editorInfo = currentInputEditorInfo ?: return false
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
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
        featureCarousel = view.findViewById(R.id.feature_carousel)
        featurePane = view.findViewById(R.id.feature_pane)
        mainView = view

        // Full-bleed "Suggestion Edit Mode" overlay, drawn above everything.
        val root = view as? android.widget.FrameLayout
        dragOverlay = SuggestionDragOverlay(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.INVISIBLE
            isClickable = false
        }
        root?.addView(dragOverlay)

        // Build clipboard + emoji panes programmatically so they share the same parent.
        clipboardPane = ClipboardPane(this).apply { visibility = View.GONE }
        emojiPane = EmojiPane(this).apply { visibility = View.GONE }
        featurePane?.addView(clipboardPane)
        featurePane?.addView(emojiPane)

        featureCarousel?.items = listOf(
            FeatureItem("Suggestions", "\uD83D\uDCAC"),
            FeatureItem("Clipboard", "\uD83D\uDCCB"),
            FeatureItem("Emoji", "\uD83D\uDE00")
        )
        featureCarousel?.onItemSelected = { index ->
            currentMode = index
            suggestionBar?.visibility = if (index == 0) View.VISIBLE else View.GONE
            clipboardPane?.visibility = if (index == 1) View.VISIBLE else View.GONE
            emojiPane?.visibility = if (index == 2) View.VISIBLE else View.GONE
            when (index) {
                1 -> refreshClipboardPane()
                2 -> refreshEmojiPane()
            }
        }

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

        // "Suggestion Edit Mode": long-press a chip and drop it on a magnetic zone.
        val overlay = dragOverlay
        if (overlay != null) {
            overlay.onRemoveRequested = { word -> suppressSuggestion(word) }
            overlay.onFavoriteRequested = { word -> favoriteSuggestion(word) }
            overlay.onDragFinished = { suggestionBar?.restoreChip() }

            suggestionBar?.onSuggestionDragStart = { chip, word, rawX, rawY ->
                overlay.endCurrentDragIfAny()
                val overlayOrigin = IntArray(2)
                overlay.getLocationOnScreen(overlayOrigin)
                val chipLoc = IntArray(2)
                chip.getLocationOnScreen(chipLoc)
                overlay.beginDrag(
                    word,
                    Rect(
                        chipLoc[0] - overlayOrigin[0],
                        chipLoc[1] - overlayOrigin[1],
                        chipLoc[0] - overlayOrigin[0] + chip.width,
                        chipLoc[1] - overlayOrigin[1] + chip.height
                    ),
                    rawX - overlayOrigin[0],
                    rawY - overlayOrigin[1]
                )
            }
            suggestionBar?.onSuggestionDragMove = { rawX, rawY ->
                val origin = IntArray(2)
                overlay.getLocationOnScreen(origin)
                overlay.updateFinger(rawX - origin[0], rawY - origin[1])
            }
            suggestionBar?.onSuggestionDragEnd = { rawX, rawY ->
                val origin = IntArray(2)
                overlay.getLocationOnScreen(origin)
                overlay.finishDrag(rawX - origin[0], rawY - origin[1])
            }
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
                        suggestionBar?.clearSuggestions()
                        val editorInfo = currentInputEditorInfo
                        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                        val multiline =
                            (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                        val noEnterAction =
                            (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                        if (multiline || noEnterAction ||
                            action == EditorInfo.IME_ACTION_NONE ||
                            action == EditorInfo.IME_ACTION_UNSPECIFIED
                        ) {
                            ic.commitText("\n", 1)
                        } else {
                            ic.performEditorAction(action)
                        }
                    }

                    "⌫" -> {
                        val restored = deletedText
                        val deleted = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                        deletedText = if (deleted == restored) restored + deleted else deleted

                        if (currentWord.isNotEmpty()) {
                            currentWord.deleteCharAt(currentWord.length - 1)
                            updateSuggestions()
                        } else {
                            // Backspace past a committed word: undo the context push
                            // so re-typing the same word gets fresh predictions.
                            if (deleted == " " && contextWords.isNotEmpty()) {
                                contextWords.removeAt(contextWords.lastIndex)
                            }
                            updateSuggestions()
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
        clipboardManager.copy(committed)
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

    private fun refreshClipboardPane() {
        // Pick up anything copied/cut in another app since the keyboard opened.
        captureSystemClipboard()
        val dark = Prefs.darkTheme(this)
        clipboardPane?.bind(
            clipboardManager,
            dark,
            onPaste = { text -> currentInputConnection?.commitText(text, 1) },
            onDelete = { text ->
                clipboardManager.removeByText(text)
                refreshClipboardPane()
            }
        )
    }

    private fun refreshEmojiPane() {
        val dark = Prefs.darkTheme(this)
        emojiPane?.bind(dark) { emoji ->
            currentInputConnection?.commitText(emoji, 1)
        }
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
            val context = currentPredictionContext()
            val suggestions = suggestionEngine.predict(context, limit = 7).map { it.word }
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
            clipboardManager.copy(word)
        }

        currentWord.clear()
        suggestionBar?.clearSuggestions()
    }

    /** Drop on REMOVE: suppress the word (reversible via undo). */
    private fun suppressSuggestion(word: String) {
        suggestionEngine.suppress(word)
        lastSuppressedWord = word
        Toast.makeText(
            this,
            getString(R.string.word_removed_confirmed, word),
            Toast.LENGTH_SHORT
        ).show()
        updateSuggestions()
        updateUndoUi()
    }

    /** Drop on FAVORITE: boost the word so it surfaces near the top. */
    private fun favoriteSuggestion(word: String) {
        suggestionEngine.favorite(word)
        Toast.makeText(
            this,
            getString(R.string.word_favorited_confirmed, word),
            Toast.LENGTH_SHORT
        ).show()
        updateSuggestions()
    }

    private fun undoDelete() {
        val ic = currentInputConnection ?: return

        // Suggestion suppression undo — re-enable the hidden word and refresh.
        val suppressed = lastSuppressedWord
        if (suppressed != null) {
            suggestionEngine.unsuppress(suppressed)
            lastSuppressedWord = null
            updateSuggestions()
            updateUndoUi()
            return
        }

        // Text-delete undo.
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
        val textAvailable = deletedText?.isNotEmpty() == true
        val suppressAvailable = lastSuppressedWord != null
        val available = textAvailable || suppressAvailable
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

        // Bulletproof: always clear a stuck overlay when a new input starts.
        dragOverlay?.endCurrentDragIfAny()

        // Invalidate any in-flight suggestion computation.
        suggestionJob++
        currentWord.clear()
        contextWords.clear()
        deletedText = null
        lastSuppressedWord = null
        suggestionBar?.clearSuggestions()
        updateUndoUi()
        keyboardView?.applyThemeIfChanged()
        applyKeyboardTheme()
        // If the user copied/cut something in another app, capture it now.
        captureSystemClipboard()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dragOverlay?.endCurrentDragIfAny()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        suggestionJob++
        suggestionExecutor.shutdownNow()
        try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
            cm?.removePrimaryClipChangedListener(systemClipboardListener)
        } catch (_: SecurityException) {
            // Ignore.
        }
        systemClipboardListener = null
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