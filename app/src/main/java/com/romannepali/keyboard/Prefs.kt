package com.romannepali.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

object Prefs {

    private const val NAME = "merotype_prefs"

    private const val KEY_VIBRATION = "key_vibration"
    private const val KEY_SOUND = "key_sound"
    private const val KEY_SUGGESTIONS = "word_suggestions"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_KEYBOARD_SIZE = "keyboard_size"
    private const val KEY_AUTOCORRECT = "auto_correct"
    private const val KEY_AUTOCAP = "auto_capitalize"
    private const val KEY_DOUBLE_SPACE = "double_space_period"
    private const val KEY_NUMBER_ROW = "number_row"
    private const val KEY_PREVIEW = "key_preview"
    private const val KEY_SPACE_SWIPE = "space_swipe_cursor"
    private const val KEY_ONE_HAND = "one_hand_mode"
    private const val KEY_ACCENT = "accent_index"
    private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
    private const val KEY_SWIPE_DELETE = "swipe_delete_word"

    /** One-handed mode values. */
    const val ONE_HAND_OFF = 0
    const val ONE_HAND_LEFT = 1
    const val ONE_HAND_RIGHT = 2

    /** Accent palette: name + ARGB. Index 0 is the original MeroType coral. */
    val ACCENTS: List<Pair<String, Int>> = listOf(
        "Coral" to Color.parseColor("#FFFF6B4A"),
        "Saffron" to Color.parseColor("#FFFFB02E"),
        "Mint" to Color.parseColor("#FF3DDC97"),
        "Sky" to Color.parseColor("#FF4DA3FF"),
        "Violet" to Color.parseColor("#FFA78BFA"),
        "Rose" to Color.parseColor("#FFFF5C8A")
    )

    fun vibration(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATION, true)

    fun vibrationIntensity(context: Context): Int =
        prefs(context).getInt(KEY_VIBRATION_INTENSITY, 60).coerceIn(0, 100)

    fun swipeDeleteWord(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SWIPE_DELETE, true)

    fun sound(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND, true)

    fun suggestions(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUGGESTIONS, true)

    fun darkTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_THEME, true)

    fun keyboardSize(context: Context): Int =
        prefs(context).getInt(KEY_KEYBOARD_SIZE, 50)

    fun autoCorrect(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOCORRECT, true)

    fun autoCapitalize(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOCAP, true)

    fun doubleSpacePeriod(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOUBLE_SPACE, true)

    fun numberRow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NUMBER_ROW, true)

    fun keyPreview(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PREVIEW, true)

    fun spaceSwipeCursor(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPACE_SWIPE, true)

    fun oneHandMode(context: Context): Int =
        prefs(context).getInt(KEY_ONE_HAND, ONE_HAND_OFF).coerceIn(0, 2)

    fun accentIndex(context: Context): Int =
        prefs(context).getInt(KEY_ACCENT, 0).coerceIn(0, ACCENTS.size - 1)

    fun accentColor(context: Context): Int = ACCENTS[accentIndex(context)].second

    fun setVibration(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun setVibrationIntensity(context: Context, intensity: Int) {
        prefs(context).edit().putInt(KEY_VIBRATION_INTENSITY, intensity.coerceIn(0, 100)).apply()
    }

    fun setSwipeDeleteWord(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SWIPE_DELETE, enabled).apply()
    }

    fun setSound(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setSuggestions(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUGGESTIONS, enabled).apply()
    }

    fun setDarkTheme(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun setKeyboardSize(context: Context, size: Int) {
        prefs(context).edit().putInt(KEY_KEYBOARD_SIZE, size.coerceIn(0, 100)).apply()
    }

    fun setAutoCorrect(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOCORRECT, enabled).apply()
    }

    fun setAutoCapitalize(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOCAP, enabled).apply()
    }

    fun setDoubleSpacePeriod(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DOUBLE_SPACE, enabled).apply()
    }

    fun setNumberRow(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NUMBER_ROW, enabled).apply()
    }

    fun setKeyPreview(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREVIEW, enabled).apply()
    }

    fun setSpaceSwipeCursor(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPACE_SWIPE, enabled).apply()
    }

    fun setOneHandMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_ONE_HAND, mode.coerceIn(0, 2)).apply()
    }

    fun setAccentIndex(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, index.coerceIn(0, ACCENTS.size - 1)).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
