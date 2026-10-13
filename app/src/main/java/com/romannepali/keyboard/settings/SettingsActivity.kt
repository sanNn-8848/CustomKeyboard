package com.romannepali.keyboard.settings

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.romannepali.keyboard.Prefs
import com.romannepali.keyboard.HapticFeedbackManager
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
        setupAppearance()
        setupDictionary()
        setupClipboard()
        setupAbout()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private var promptedEnable = false

    override fun onResume() {
        super.onResume()
        refreshSetupStatus()
    }

    private fun setupKeyboardButton() {
        refreshSetupStatus()
        if (!isKeyboardEnabled() && !promptedEnable) {
            promptedEnable = true
            showEnableKeyboardDialog()
        }
    }

    /** Two-step status: enabled in system list -> currently selected. */
    private fun refreshSetupStatus() {
        val status = findViewById<TextView>(R.id.text_setup_status)
        val detail = findViewById<TextView>(R.id.text_setup_detail)
        val button = findViewById<MaterialButton>(R.id.btn_setup_keyboard)
        val tryField = findViewById<EditText>(R.id.input_try_keyboard)
        when {
            !isKeyboardEnabled() -> {
                status.setText(R.string.status_not_enabled)
                detail.setText(R.string.status_not_enabled_detail)
                button.setText(R.string.btn_enable)
                button.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
                tryField.visibility = View.GONE
            }
            !isKeyboardSelected() -> {
                status.setText(R.string.status_not_selected)
                detail.setText(R.string.status_not_selected_detail)
                button.setText(R.string.btn_switch)
                button.setOnClickListener {
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showInputMethodPicker()
                }
                tryField.visibility = View.GONE
            }
            else -> {
                status.setText(R.string.status_active)
                detail.setText(R.string.status_active_detail)
                button.setText(R.string.btn_keyboard_settings)
                button.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
                tryField.visibility = View.VISIBLE
            }
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any {
            it.packageName == packageName
        }
    }

    private fun isKeyboardSelected(): Boolean {
        val current = Settings.Secure.getString(
            contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return current.startsWith("$packageName/")
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

        val vibSlider = findViewById<Slider>(R.id.slider_vibration)
        val vibValue = findViewById<TextView>(R.id.text_vibration_value)
        vibSlider.value = Prefs.vibrationIntensity(this).toFloat()
        vibValue.text = getString(R.string.vibration_value, Prefs.vibrationIntensity(this))
        vibSlider.addOnChangeListener { _, value, _ ->
            Prefs.setVibrationIntensity(this, value.toInt())
            vibValue.text = getString(R.string.vibration_value, value.toInt())
        }
        findViewById<MaterialButton>(R.id.btn_test_vibration).setOnClickListener {
            HapticFeedbackManager(this).testVibration()
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

        bindSwitch(R.id.switch_autocorrect, Prefs.autoCorrect(this)) { Prefs.setAutoCorrect(this, it) }
        bindSwitch(R.id.switch_autocap, Prefs.autoCapitalize(this)) { Prefs.setAutoCapitalize(this, it) }
        bindSwitch(R.id.switch_double_space, Prefs.doubleSpacePeriod(this)) { Prefs.setDoubleSpacePeriod(this, it) }
        bindSwitch(R.id.switch_number_row, Prefs.numberRow(this)) { Prefs.setNumberRow(this, it) }
        bindSwitch(R.id.switch_space_swipe, Prefs.spaceSwipeCursor(this)) { Prefs.setSpaceSwipeCursor(this, it) }
        bindSwitch(R.id.switch_swipe_delete, Prefs.swipeDeleteWord(this)) { Prefs.setSwipeDeleteWord(this, it) }
        bindSwitch(R.id.switch_key_preview, Prefs.keyPreview(this)) { Prefs.setKeyPreview(this, it) }

        val sizeSlider = findViewById<Slider>(R.id.slider_keyboard_size)
        val sizeValue = findViewById<TextView>(R.id.text_keyboard_size_value)
        sizeSlider.value = Prefs.keyboardSize(this).toFloat()
        sizeValue.text = getString(keyboardSizeLabel(Prefs.keyboardSize(this)))
        sizeSlider.addOnChangeListener { _, value, _ ->
            Prefs.setKeyboardSize(this, value.toInt())
            sizeValue.text = getString(keyboardSizeLabel(value.toInt()))
        }
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val view = findViewById<SwitchMaterial>(id)
        view.isChecked = initial
        view.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    /** Accent colour swatches + one-handed mode selector. */
    private fun setupAppearance() {
        val row = findViewById<LinearLayout>(R.id.accent_row)
        row.removeAllViews()
        val size = dp(36)
        val selected = Prefs.accentIndex(this)
        val ringColor = if (Prefs.darkTheme(this)) Color.WHITE else Color.BLACK
        Prefs.ACCENTS.forEachIndexed { index, (name, color) ->
            val swatch = View(this).apply {
                contentDescription = name
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (index == selected) setStroke(dp(3), ringColor)
                }
                setOnClickListener {
                    Prefs.setAccentIndex(this@SettingsActivity, index)
                    setupAppearance()
                }
            }
            row.addView(
                swatch,
                LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(12) }
            )
        }

        val group = findViewById<MaterialButtonToggleGroup>(R.id.group_one_hand)
        group.clearOnButtonCheckedListeners()
        group.check(
            when (Prefs.oneHandMode(this)) {
                Prefs.ONE_HAND_LEFT -> R.id.btn_one_hand_left
                Prefs.ONE_HAND_RIGHT -> R.id.btn_one_hand_right
                else -> R.id.btn_one_hand_off
            }
        )
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            Prefs.setOneHandMode(
                this,
                when (checkedId) {
                    R.id.btn_one_hand_left -> Prefs.ONE_HAND_LEFT
                    R.id.btn_one_hand_right -> Prefs.ONE_HAND_RIGHT
                    else -> Prefs.ONE_HAND_OFF
                }
            )
        }
    }

    private fun keyboardSizeLabel(size: Int): Int = when {
        size < 35 -> R.string.keyboard_size_small
        size > 65 -> R.string.keyboard_size_large
        else -> R.string.keyboard_size_normal
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
                Triple(word, word) { _, w ->
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
            engine.getLearnedWords().take(20).map { (word, count) ->
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

