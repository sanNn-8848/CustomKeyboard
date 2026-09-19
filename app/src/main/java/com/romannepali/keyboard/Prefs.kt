package com.romannepali.keyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val NAME = "merotype_prefs"

    private const val KEY_VIBRATION = "key_vibration"
    private const val KEY_SOUND = "key_sound"
    private const val KEY_SUGGESTIONS = "word_suggestions"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_KEYBOARD_SIZE = "keyboard_size"

    fun vibration(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATION, true)

    fun sound(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND, true)

    fun suggestions(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUGGESTIONS, true)

    fun darkTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_THEME, true)

    fun keyboardSize(context: Context): Int =
        prefs(context).getInt(KEY_KEYBOARD_SIZE, 50)

    fun setVibration(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
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

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}