package com.romannepali.keyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {

    private const val NAME = "merotype_prefs"

    private const val KEY_VIBRATION = "key_vibration"
    private const val KEY_SUGGESTIONS = "word_suggestions"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_NUMBER_ROW = "number_row"

    fun vibration(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATION, true)

    fun suggestions(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUGGESTIONS, true)

    fun darkTheme(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DARK_THEME, true)

    fun numberRow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NUMBER_ROW, false)

    fun setVibration(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply()
    }

    fun setSuggestions(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUGGESTIONS, enabled).apply()
    }

    fun setDarkTheme(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun setNumberRow(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NUMBER_ROW, enabled).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}