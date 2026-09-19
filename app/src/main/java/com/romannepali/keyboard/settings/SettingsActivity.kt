package com.romannepali.keyboard.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.romannepali.keyboard.Prefs
import com.romannepali.keyboard.R
import com.romannepali.keyboard.clipboard.ClipboardManager
import com.romannepali.keyboard.suggestion.SuggestionEngine

class SettingsActivity : AppCompatActivity() {

    private val engine by lazy { SuggestionEngine(this) }
    private val clipboardManager by lazy { ClipboardManager.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (Prefs.darkTheme(this)) R.style.Theme_MeroType_Dark
            else R.style.Theme_MeroType_Light
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        setupKeyboardButton()
        setupSwitches()
        setupDictionary()
        setupClipboard()
        setupAbout()
    }

    private fun setupKeyboardButton() {
        val button = findViewById<MaterialButton>(R.id.btn_setup_keyboard)
        if (isKeyboardEnabled()) {
            button.text = getString(R.string.setup_keyboard)
        }
        button.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }
        if (!isKeyboardEnabled()) {
            showEnableKeyboardDialog()
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any {
            it.packageName == packageName
        }
    }

    private fun showEnableKeyboardDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.enable_keyboard_dialog_title)
            .setMessage(R.string.enable_keyboard_dialog_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun setupSwitches() {
        val dark = findViewById<SwitchMaterial>(R.id.switch_dark_theme)
        dark.isChecked = Prefs.darkTheme(this)
        dark.setOnCheckedChangeListener { _, checked ->
            Prefs.setDarkTheme(this, checked)
            recreate()
        }

        val vibration = findViewById<SwitchMaterial>(R.id.switch_vibration)
        vibration.isChecked = Prefs.vibration(this)
        vibration.setOnCheckedChangeListener { _, checked ->
            Prefs.setVibration(this, checked)
        }

        val keyAudio = findViewById<SwitchMaterial>(R.id.switch_key_audio)
        keyAudio.isChecked = Prefs.sound(this)
        keyAudio.setOnCheckedChangeListener { _, checked ->
            Prefs.setSound(this, checked)
        }

        val suggestions = findViewById<SwitchMaterial>(R.id.switch_suggestions)
        suggestions.isChecked = Prefs.suggestions(this)
        suggestions.setOnCheckedChangeListener { _, checked ->
            Prefs.setSuggestions(this, checked)
        }

        val numberRow = findViewById<SwitchMaterial>(R.id.switch_number_row)
        numberRow.isChecked = Prefs.numberRow(this)
        numberRow.setOnCheckedChangeListener { _, checked ->
            Prefs.setNumberRow(this, checked)
        }
    }

    private fun setupDictionary() {
        val input = findViewById<EditText>(R.id.input_new_word)
        findViewById<MaterialButton>(R.id.btn_add_word).setOnClickListener {
            val word = input.text.toString().trim()
            when {
                word.isEmpty() -> Toast.makeText(this, R.string.empty_word, Toast.LENGTH_SHORT).show()
                !engine.addPersonalWord(word) ->
                    Toast.makeText(this, R.string.word_invalid, Toast.LENGTH_SHORT).show()
                else -> {
                    input.text?.clear()
                    Toast.makeText(
                        this,
                        getString(R.string.word_added, word),
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshDictionary()
                }
            }
        }

        findViewById<MaterialButton>(R.id.btn_clear_learned).setOnClickListener {
            val cleared = engine.getLearnedWords().toMap()
            if (cleared.isEmpty()) return@setOnClickListener
            engine.clearLearnedWords()
            refreshDictionary()
            snackbar(getString(R.string.clear_learned_confirmed)) {
                engine.restoreLearned(cleared)
                refreshDictionary()
            }
        }

        refreshDictionary()
    }

    private fun refreshDictionary() {
        renderRows(
            R.id.saved_list,
            R.id.saved_empty,
            engine.getSavedWords().map { word ->
                Triple(word, "") { _, w ->
                    engine.removePersonalWord(w)
                    refreshDictionary()
                    snackbar(getString(R.string.word_removed_confirmed, w)) {
                        engine.addPersonalWord(w)
                        refreshDictionary()
                    }
                }
            }
        )

        renderRows(
            R.id.learned_list,
            R.id.learned_empty,
            engine.getLearnedWords().map { (word, count) ->
                Triple("$word · $count", word) { _, w ->
                    val backup = engine.getLearnedWords().firstOrNull { it.first == w }
                    engine.removeLearnedWord(w)
                    refreshDictionary()
                    snackbar(getString(R.string.word_removed_confirmed, w)) {
                        backup?.let { engine.restoreLearned(mapOf(it)) }
                        refreshDictionary()
                    }
                }
            }
        )

        renderRows(
            R.id.suppressed_list,
            R.id.suppressed_empty,
            engine.getSuppressedWords().map { word ->
                Triple(word, word) { _, w ->
                    engine.unsuppress(w)
                    refreshDictionary()
                    snackbar(getString(R.string.word_restored_confirmed, w)) {
                        engine.suppress(w)
                        refreshDictionary()
                    }
                }
            }
        )
    }

    private fun setupClipboard() {
        findViewById<MaterialButton>(R.id.btn_clear_clipboard).setOnClickListener {
            val cleared = clipboardManager.getHistory()
            if (cleared.isEmpty()) return@setOnClickListener
            clipboardManager.clearHistory()
            refreshClipboard()
            snackbar(getString(R.string.clear_clipboard_confirmed)) {
                cleared.reversed().forEach { clipboardManager.copy(it.text) }
                refreshClipboard()
            }
        }

        refreshClipboard()
    }

    private fun refreshClipboard() {
        renderRows(
            R.id.clipboard_list,
            R.id.clipboard_empty,
            clipboardManager.getHistory().take(6).map { item ->
                Triple(item.text, item.text) { _, text ->
                    clipboardManager.removeByText(text)
                    refreshClipboard()
                    snackbar(getString(R.string.clip_removed_confirmed)) {
                        clipboardManager.copy(text)
                        refreshClipboard()
                    }
                }
            }
        )
    }

    private fun renderRows(
        containerId: Int,
        emptyId: Int,
        items: List<Triple<String, String, (View, String) -> Unit>>
    ) {
        val container = findViewById<LinearLayout>(containerId)
        val empty = findViewById<TextView>(emptyId)
        container.removeAllViews()

        val dark = Prefs.darkTheme(this)
        val textColor = if (dark) "#E8EAED" else "#202124"
        val accentColor = if (dark) "#E6E8EB" else "#3C4043"

        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.forEach { (label, key, onDelete) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }

            val labelView = TextView(this).apply {
                this.text = label
                textSize = 15f
                setTextColor(Color.parseColor(textColor))
                gravity = Gravity.START
                maxLines = 1
            }

            val removeView = TextView(this).apply {
                text = "\u2715"
                textSize = 18f
                setPadding(dp(10), dp(4), dp(6), dp(4))
                setTextColor(Color.parseColor(accentColor))
                setOnClickListener { onDelete(it, key) }
            }

            row.addView(
                labelView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.addView(removeView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            container.addView(row)
        }
    }

    private fun setupAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        findViewById<TextView>(R.id.about_version).text = "MeroType v$version · Open source"
    }

    private fun snackbar(message: String, onUndo: () -> Unit) {
        Snackbar.make(findViewById(R.id.root), message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { onUndo() }
            .show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}