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
import com.romannepali.keyboard.clipboard.ClipAction
import com.romannepali.keyboard.clipboard.ClipboardManager
import com.romannepali.keyboard.clipboard.ClipboardPane
import com.romannepali.keyboard.editing.TextEditor
import com.romannepali.keyboard.emoji.EmojiSearchPane
import com.romannepali.keyboard.suggestion.AUTO_CORRECT_THRESHOLD
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
    private var emojiSearchPane: EmojiSearchPane? = null
    private var mainView: View? = null

    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var clipboardManager: ClipboardManager
    private var clipListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    private val textEditor = TextEditor()
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

    override fun onCreate() {
        super.onCreate()

        suggestionEngine = SuggestionEngine(this)
        clipboardManager = ClipboardManager.get(this)

        // Watch the system clipboard so ANY copy/cut (this keyboard's or any
        // other app's) reaches the pane's history. Without this listener the
        // store had no writer at all after the Actions panel was removed.
        clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            // ClipboardManager can fire this off the main thread; hop over so
            // the store write + pane re-render stay ordered on the UI thread.
            mainHandler.post {
                saveEditedClip {
                    if (currentMode == 1) refreshClipboardPane()
                }
            }
        }
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager)
            ?.addPrimaryClipChangedListener(clipListener)

        updateFullscreenMode()
    }

    /**
     * After the user explicitly uses the toolbar's Copy/Cut, capture the text
     * that lands on the system clipboard into history. The host app usually
     * publishes the primary clip asynchronously, so the read is retried a few
     * times; [knownText] (the selection read before a Cut) is stored immediately
     * as a fallback that never depends on host timing.
     */
    private fun saveEditedClip(
        knownText: String? = null,
        attempt: Int = 0,
        onDone: (() -> Unit)? = null
    ) {
        if (!knownText.isNullOrBlank() && attempt == 0) {
            clipboardManager.copy(knownText)
        }
        val systemText = readPrimaryClipText()
        if (!systemText.isNullOrBlank()) {
            clipboardManager.copy(systemText)
            onDone?.invoke()
        } else if (attempt < 5) {
            mainHandler.postDelayed({ saveEditedClip(null, attempt + 1, onDone) }, 100L)
        } else {
            onDone?.invoke()
        }
    }

    /** Reads the current system clip, or null if not ready / unavailable. */
    private fun readPrimaryClipText(): String? {
        return try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager ?: return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            clip.getItemAt(0).coerceToText(this).toString().takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            // Shortcut or restricted IME — nothing we can do.
            null
        } catch (_: IllegalStateException) {
            // Primary clip not yet ready.
            null
        }
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

        // Build the two feature panes programmatically so they share the same parent.
        clipboardPane = ClipboardPane(this).apply {
            visibility = View.GONE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        emojiSearchPane = EmojiSearchPane(this).apply {
            visibility = View.GONE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        featurePane?.addView(clipboardPane)
        featurePane?.addView(emojiSearchPane)

        featureCarousel?.items = listOf(
            FeatureItem("Suggestions", "\uD83D\uDCAC"),
            FeatureItem("Clipboard", "\uD83D\uDCCB"),
            FeatureItem("Emoji", "\uD83D\uDE00")
        )
        applyKeyboardTheme()

        // Long-pressing the comma key opens the Emoji search pane.
        keyboardView?.onEmojiRequested = { openEmojiSearch() }

        // Restore the last mode (matters after rotation, e.g. emoji pane open).
        showFeatureMode(currentMode)
        // Sync the carousel highlight to the restored mode too. The listener
        // attaches after this line so select() can't rebuild the pane above.
        featureCarousel?.select(currentMode)
        featureCarousel?.onItemSelected = { index ->
            showFeatureMode(index)
        }

        // Keep the keyboard above system navigation/gesture bars in all orientations.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        suggestionBar?.onSuggestionClickListener = { suggestion ->
            applySuggestion(suggestion)
        }
        suggestionBar?.onSuggestionDoubleClickListener = { suggestion ->
            applySuggestion(suggestion, cycleCase = true)
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

            // While the emoji search field is the active target, free-text keys
            // drive the search filter instead of the app's text field.
            if (emojiSearchPane?.isSearchTarget() == true) {
                emojiSearchPane?.consumeKey(text)
            } else if (ic != null) {
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
                        val imeOptions = editorInfo?.imeOptions ?: 0
                        val action = imeOptions and EditorInfo.IME_MASK_ACTION
                        val inputType = editorInfo?.inputType ?: 0

                        val noEnterAction =
                            (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

                        // An explicit editor action wins over a multiline flag so
                        // search/URL fields really submit instead of inserting "\n".
                        val hasExplicitAction =
                            action == EditorInfo.IME_ACTION_GO ||
                                action == EditorInfo.IME_ACTION_SEARCH ||
                                action == EditorInfo.IME_ACTION_NEXT ||
                                action == EditorInfo.IME_ACTION_DONE ||
                                action == EditorInfo.IME_ACTION_SEND

                        val multiline =
                            (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

                        when {
                            hasExplicitAction && !noEnterAction -> {
                                ic.performEditorAction(action)
                            }

                            multiline || noEnterAction ||
                                action == EditorInfo.IME_ACTION_NONE ||
                                action == EditorInfo.IME_ACTION_UNSPECIFIED -> {
                                ic.commitText("\n", 1)
                            }

                            else -> {
                                ic.performEditorAction(action)
                            }
                        }
                    }

                    "⌫" -> {
                        val selected = ic.getSelectedText(0)?.toString().orEmpty()
                        if (selected.isNotEmpty()) {
                            // Deleting a highlighted range: replace it with nothing.
                            deletedText = selected
                            currentWord.clear()
                            ic.commitText("", 1)
                            suggestionBar?.clearSuggestions()
                        } else {
                            val restored = deletedText
                            val deleted = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                            deletedText = if (deleted == restored) restored + deleted else deleted

                            if (currentWord.isNotEmpty()) {
                                val actual = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()

                                if (actual.length == 1 &&
                                    (actual[0].isLetter() || actual[0] == '\'')
                                ) {
                                    currentWord.deleteCharAt(currentWord.length - 1)
                                    updateSuggestions()
                                } else {
                                    // The editor and our cached word disagree.
                                    // Re-sync instead of corrupting currentWord.
                                    currentWord.clear()
                                    suggestionJob++
                                    suggestionBar?.clearSuggestions()
                                }
                            } else {
                                // Do not modify contextWords merely because the character before
                                // the cursor is a space. The cursor may have moved, or the user
                                // may be editing text that was not produced by this IME session.
                                // contextWords is prediction state, not a reliable representation
                                // of the editor's actual document.
                                updateSuggestions()
                            }

                            ic.deleteSurroundingText(1, 0)
                        }
                    }

                    "⇧" -> {
                        // Shift is handled inside GboardKeyboardView.
                    }

                    else -> {
                        ic.commitText(text, 1)
                        if (text.length == 1 && text[0].isLetter()) {
                            currentWord.append(text.lowercase())
                            updateSuggestions()
                        }
                    }
                }

                updateUndoUi()
                updateAutoShift()
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
     * Deletes exactly [expectedText] immediately before the cursor.
     *
     * Returns true only when the editor confirms that the text immediately
     * before the cursor matches what we intend to remove.
     *
     * This prevents a stale currentWord from deleting unrelated text.
     */
    private fun deleteExpectedTextBeforeCursor(
        ic: android.view.inputmethod.InputConnection,
        expectedText: String
    ): Boolean {
        if (expectedText.isEmpty()) return true

        val actual = ic.getTextBeforeCursor(expectedText.length, 0)
            ?.toString()
            ?: return false

        if (actual != expectedText) {
            return false
        }

        return ic.deleteSurroundingText(expectedText.length, 0)
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
            if (correction.isCorrection &&
                correction.confidence >= AUTO_CORRECT_THRESHOLD
            ) {
                if (deleteExpectedTextBeforeCursor(ic, raw)) {
                    ic.commitText(correction.word, 1)
                    committed = correction.word
                } else {
                    // Editor text no longer matches our tracked word.
                    // Never perform a blind deletion.
                    currentWord.clear()
                    suggestionJob++
                    return
                }
            }
        }

        suggestionEngine.learnWord(committed)

        val previousWord = contextWords.lastOrNull()
        suggestionEngine.updateContext(previousWord, committed)

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

    // Auto-capitalization: applied ONCE per sentence start, and only so long as
    // the user doesn't override it with a manual shift tap. Manual shift is
    // always respected — the auto state never fights back.
    private var autoShiftApplied = false
    private var userShiftOverride = false

    private fun updateAutoShift() {
        val kb = keyboardView ?: return
        if (isSentenceStart()) {
            if (!autoShiftApplied && !userShiftOverride) {
                kb.setShifted(true)
                autoShiftApplied = true
            }
        } else {
            autoShiftApplied = false
            userShiftOverride = false
        }
    }

    private fun refreshClipboardPane() {
        val dark = Prefs.darkTheme(this)
        val hasSelection = currentInputConnection?.getSelectedText(0)
            ?.toString()?.isNotEmpty() == true
        clipboardPane?.bind(
            clipboardManager,
            dark,
            hasSelection,
            onPaste = { text ->
                if (currentWord.isNotEmpty()) finishCurrentWord()
                currentInputConnection?.commitText(text, 1)
                suggestionBar?.clearSuggestions()
            },
            onDelete = { text ->
                clipboardManager.removeByText(text)
                refreshClipboardPane()
            },
            onAction = { action ->
                currentInputConnection?.let { ic ->
                    // Same contract as paste: close the in-progress word first so
                    // the buffer can't drift across an editor-side edit.
                    if (currentWord.isNotEmpty()) finishCurrentWord()
                    when (action) {
                        ClipAction.SELECT_ALL -> textEditor.selectAll(ic)
                        ClipAction.COPY -> textEditor.copy(ic)
                        ClipAction.CUT -> textEditor.cut(ic)
                        ClipAction.PASTE -> textEditor.paste(ic)
                    }
                    suggestionBar?.clearSuggestions()
                    // The selection state settles asynchronously after the action,
                    // so re-read it and refresh the action chips' enabled states.
                    mainHandler.postDelayed({ refreshClipboardPane() }, 120)
                }
            }
        )
    }

    private fun refreshEmojiSearchPane() {
        emojiSearchPane?.open { emoji ->
            // Same contract as paste: close the in-progress word first so the
            // buffer can't drift across the inserted emoji and learn junk.
            if (currentWord.isNotEmpty()) finishCurrentWord()
            currentInputConnection?.commitText(emoji, 1)
            suggestionBar?.clearSuggestions()
        }
    }

    /**
     * Activates one of the three feature modes (0=suggestions, 1=clipboard,
     * 2=emoji) and sizes the pane so emoji gets room to search & browse.
     */
    private fun showFeatureMode(index: Int) {
        currentMode = index
        suggestionBar?.visibility = if (index == 0) View.VISIBLE else View.GONE
        clipboardPane?.visibility = if (index == 1) View.VISIBLE else View.GONE
        emojiSearchPane?.visibility = if (index == 2) View.VISIBLE else View.GONE
        keyboardView?.setEmojiMode(index == 2)
        when (index) {
            1 -> {
                // Opening the pane makes the IME the focused clipboard reader:
                // pull whatever is on the system clipboard now, then re-render
                // once the (async) read settles. The change listener covers
                // copies made while we were already alive.
                saveEditedClip { refreshClipboardPane() }
                refreshClipboardPane()
            }

            2 -> refreshEmojiSearchPane()
        }
        applyFeaturePaneHeight()
    }

    private fun applyFeaturePaneHeight() {
        val landscape =
            resources.configuration.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val target = when {
            currentMode == 2 -> if (landscape) dp(120) else dp(150)
            else -> if (landscape) dp(26) else dp(42)
        }
        featurePane?.layoutParams = (featurePane?.layoutParams ?: return).apply {
            height = target
        }
    }

    /** Comma-key long press: open (or refocus) the Emoji search pane. */
    private fun openEmojiSearch() {
        if (currentMode != 2) {
            featureCarousel?.select(2)
            return
        }
        // Already typing in the search line: the view sent "⌫" first and
        // consumeKey() already removed the comma touch-down typed there.
        // Re-running open() would wipe the query the user searched for.
        if (emojiSearchPane?.isSearchTarget() == true) return
        emojiSearchPane?.open { emoji ->
            if (currentWord.isNotEmpty()) finishCurrentWord()
            currentInputConnection?.commitText(emoji, 1)
            suggestionBar?.clearSuggestions()
        }
        emojiSearchPane?.deactivateSearch()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun updateSuggestions() {
        if (!Prefs.suggestions(this)) {
            mainHandler.post { suggestionBar?.clearSuggestions() }
            return
        }

        // Snapshot all mutable typing state on the UI thread.
        val wordSnapshot = currentWord.toString()

        if (wordSnapshot.isEmpty()) {
            suggestionJob++
            mainHandler.post { suggestionBar?.clearSuggestions() }
            return
        }

        val contextSnapshot = contextWords.toList()
        val sentenceStartSnapshot = isSentenceStart()

        // Invalidate every older job before starting this one.
        val job = ++suggestionJob

        val context = PredictionContext(
            currentWord = wordSnapshot,
            previousWords = contextSnapshot,
            sentenceStart = sentenceStartSnapshot,
            cursorPosition = wordSnapshot.length
        )

        suggestionExecutor.execute {
            val suggestions = try {
                suggestionEngine
                    .predict(context, limit = 7)
                    .map { it.word }
            } catch (_: Exception) {
                emptyList()
            }

            mainHandler.post {
                if (job != suggestionJob) return@post

                // Do one final state check before displaying the result.
                if (currentWord.toString() != wordSnapshot) {
                    return@post
                }

                suggestionBar?.showSuggestions(suggestions.map { suggestionEngine.applyCase(it) })
            }
        }
    }

    private fun applySuggestion(suggestion: String, cycleCase: Boolean = false) {
        val ic = currentInputConnection ?: return

        var out = suggestionEngine.applyCase(suggestion)
        if (cycleCase) {
            out = suggestionEngine.cycleCase(suggestion)
        }

        if (currentWord.isNotEmpty()) {
            val expected = currentWord.toString()
            val actual = ic.getTextBeforeCursor(expected.length, 0)?.toString()

            if (actual != expected) {
                // currentWord may be stale. Re-sync from the editor; the safe
                // deletion below then matches the true current text.
                val synced = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty()
                var syncStart = synced.length
                while (syncStart > 0) {
                    val c = synced[syncStart - 1]
                    if (c.isWhitespace() || c == '.' || c == ',' || c == ';') break
                    syncStart--
                }
                val editorWord = synced.substring(syncStart)
                if (editorWord.isEmpty()) {
                    // No typed word before the cursor: nothing to replace.
                    currentWord.clear()
                    suggestionJob++
                    suggestionBar?.clearSuggestions()
                    updateUndoUi()
                    return
                }
                currentWord.setLength(0)
                currentWord.append(editorWord)
            }

            if (!deleteExpectedTextBeforeCursor(ic, currentWord.toString())) {
                // The editor text still does not match our tracked word.
                // Never perform a blind deletion.
                currentWord.clear()
                suggestionJob++
                suggestionBar?.clearSuggestions()
                updateUndoUi()
                return
            }
        }

        // Commit the word then a space, so the next word starts clean and the
        // keyboard behaves like the platform picker the user knows.
        ic.commitText(out + " ", 1)

        val words = suggestion.split(Regex("\\s+")).filter { it.isNotBlank() }
        words.forEach { word ->
            val previousWord = contextWords.lastOrNull()

            suggestionEngine.learnWord(word.lowercase())
            suggestionEngine.updateContext(previousWord, word.lowercase())

            pushContext(word.lowercase())
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

        // The keyboard view re-measures itself for the new orientation and does
        // not rebuild (rotation stability: suggestion/word/shift state survive).
        // Here we only make sure the system-bar inset is re-applied afterwards.
        mainHandler.post {
            if (mainView != null) {
                mainView?.requestApplyInsets()
            }
        }
    }

    override fun onStartInput(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInput(attribute, restarting)

        // Bulletproof: always clear a stuck overlay when a new input starts.
        dragOverlay?.endCurrentDragIfAny()

        // The typed word and suggestions are NOT cleared here: rotation re-enters
        // the same editor with restarting=true/false, and onUpdateSelection then
        // synchronizes the cached word and suggestion bar against the editor's
        // real text (self-correcting). Clearing here is what made rotation look
        // like a keyboard restart ("mero nam" + suggestions vanishing).
        deletedText = null
        lastSuppressedWord = null
        updateUndoUi()
        autoShiftApplied = false
        userShiftOverride = false

        keyboardView?.onShiftToggled = { _ ->
            if (isSentenceStart()) userShiftOverride = true
        }
        updateAutoShift()
        keyboardView?.applyThemeIfChanged()
        applyKeyboardTheme()

        // Reflect the target editor's IME action (Search/Go/Next/Send) on the
        // Enter key. Re-applied on every input start, including rotation.
        keyboardView?.setEnterAction(attribute?.imeOptions ?: 0)

        // Re-render suggestions for whatever word survives in the editor. On
        // rotation this brings back the suggestions bar for "mero nam"; in a
        // genuinely new (empty) field it clears to nothing via the same path.
        updateSuggestions()
    }

    /**
     * Runs after the input view is (re)built and attached — the guaranteed
     * moment the Enter key exists. onStartInput alone can fire before
     * onCreateInputView on the first show, leaving the icon unapplied.
     */
    override fun onStartInputView(
        editorInfo: android.view.inputmethod.EditorInfo?,
        restarting: Boolean
    ) {
        super.onStartInputView(editorInfo, restarting)
        keyboardView?.setEnterAction(editorInfo?.imeOptions ?: 0)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        val ic = currentInputConnection ?: return

        // A selection means our internally tracked word can no longer be
        // trusted. The user is editing an arbitrary range of the document.
        if (newSelStart != newSelEnd) {
            currentWord.clear()
            suggestionJob++
            mainHandler.post {
                suggestionBar?.clearSuggestions()
            }
            updateUndoUi()
            return
        }

        // Read the actual text immediately before the cursor instead of
        // trusting the keyboard's cached currentWord.
        val beforeCursor = ic.getTextBeforeCursor(MAX_SYNC_CHARS, 0)
            ?.toString()
            ?: return

        val actualWord = beforeCursor
            .takeLastWhile { it.isLetter() || it == '\'' }
            .lowercase()

        // If the editor's actual word differs from our cached word, the
        // user/app changed the text or cursor outside our normal key path.
        if (actualWord != currentWord.toString()) {
            currentWord.clear()
            currentWord.append(actualWord)

            suggestionJob++

            if (actualWord.isEmpty()) {
                mainHandler.post {
                    suggestionBar?.clearSuggestions()
                }
            } else {
                updateSuggestions()
            }

            updateUndoUi()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dragOverlay?.endCurrentDragIfAny()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager)
            ?.removePrimaryClipChangedListener(clipListener)
        clipListener = null
        suggestionJob++
        suggestionExecutor.shutdownNow()
        keyboardView?.onKeyPressed = null
        keyboardView = null
        suggestionBar = null
        emojiSearchPane = null

        super.onDestroy()
    }

    private companion object {
        /** How many characters to read when re-deriving the word before the caret. */
        const val MAX_SYNC_CHARS = 64
    }
}