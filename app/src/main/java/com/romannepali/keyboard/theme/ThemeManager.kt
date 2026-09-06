package com.romannepali.keyboard.theme

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.KeyboardView

data class KeyboardTheme(
    val name: String,
    val backgroundColor: Int,
    val keyBackground: Int,
    val keyTextColor: Int,
    val suggestionBackground: Int,
    val suggestionTextColor: Int,
    val specialKeyBackground: Int,
    val isDark: Boolean
)

class ThemeManager(private val context: Context) {
    
    private val themes = mapOf(
        "dark" to KeyboardTheme(
            name = "Dark",
            backgroundColor = Color.parseColor("#FF212121"),
            keyBackground = Color.parseColor("#FF424242"),
            keyTextColor = Color.WHITE,
            suggestionBackground = Color.parseColor("#FF303030"),
            suggestionTextColor = Color.WHITE,
            specialKeyBackground = Color.parseColor("#FF616161"),
            isDark = true
        ),
        "light" to KeyboardTheme(
            name = "Light",
            backgroundColor = Color.parseColor("#FFF5F5F5"),
            keyBackground = Color.parseColor("#FFFFFFFF"),
            keyTextColor = Color.BLACK,
            suggestionBackground = Color.parseColor("#FFE0E0E0"),
            suggestionTextColor = Color.BLACK,
            specialKeyBackground = Color.parseColor("#FFD0D0D0"),
            isDark = false
        ),
        "nepali" to KeyboardTheme(
            name = "Nepali Flag",
            backgroundColor = Color.parseColor("#FFDC143C"),
            keyBackground = Color.parseColor("#FF003893"),
            keyTextColor = Color.WHITE,
            suggestionBackground = Color.parseColor("#FFDC143C"),
            suggestionTextColor = Color.WHITE,
            specialKeyBackground = Color.parseColor("#FF003893"),
            isDark = true
        ),
        "ocean" to KeyboardTheme(
            name = "Ocean",
            backgroundColor = Color.parseColor("#FF1A3A4A"),
            keyBackground = Color.parseColor("#FF2A5A6A"),
            keyTextColor = Color.WHITE,
            suggestionBackground = Color.parseColor("#FF0A2A3A"),
            suggestionTextColor = Color.WHITE,
            specialKeyBackground = Color.parseColor("#FF3A7A8A"),
            isDark = true
        ),
        "forest" to KeyboardTheme(
            name = "Forest",
            backgroundColor = Color.parseColor("#FF1A2A1A"),
            keyBackground = Color.parseColor("#FF2A4A2A"),
            keyTextColor = Color.WHITE,
            suggestionBackground = Color.parseColor("#FF0A1A0A"),
            suggestionTextColor = Color.WHITE,
            specialKeyBackground = Color.parseColor("#FF3A6A3A"),
            isDark = true
        )
    )
    
    private var currentTheme = "dark"
    
    fun getCurrentTheme(): KeyboardTheme {
        return themes[currentTheme] ?: themes["dark"]!!
    }
    
    fun setTheme(themeName: String) {
        if (themes.containsKey(themeName)) {
            currentTheme = themeName
            saveThemePreference()
        }
    }
    
    fun getAvailableThemes(): List<String> {
        return themes.keys.toList()
    }
    
    fun applyTheme(keyboardView: KeyboardView) {
        val theme = getCurrentTheme()
        keyboardView.setBackgroundColor(theme.backgroundColor)
    }
    
    private fun saveThemePreference() {
        context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("theme", currentTheme)
            .apply()
    }
    
    private fun loadThemePreference() {
        currentTheme = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            .getString("theme", "dark") ?: "dark"
    }
    
    init {
        loadThemePreference()
    }
}
