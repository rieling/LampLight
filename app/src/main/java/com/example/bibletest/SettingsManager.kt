// File: SettingsManager.kt
package com.example.bibletest

import android.content.Context
import android.graphics.Typeface
import com.google.gson.Gson


data class AppTheme(
    val name: String,          // Theme name
    val backgroundColor: Int,  // Overall background
    val textColor: Int,        // Verse text color
    val redLetterColor: Int,   // Color for red letters
    val toolbarColor: Int,     // Top bar color
    val bottomNavColor: Int    // Bottom bar color
)

object SettingsManager {

    private const val PREFS_NAME = "bible_prefs"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_FONT_NAME = "font_name"
    private const val KEY_PARAGRAPH_MODE = "paragraph_mode"
    private const val KEY_CURRENT_THEME = "current_theme"
    private const val KEY_CUSTOM_THEMES = "custom_themes"

    private val prebuiltThemes = listOf(
        AppTheme("Light", 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFFEEEEEE.toInt(), 0xFFDDDDDD.toInt()),
        AppTheme("Dark", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF5555.toInt(), 0xFF222222.toInt(), 0xFF333333.toInt())
    )

    // ---------------- Font & Paragraph ----------------
    fun getFontSize(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_FONT_SIZE, 18f)
    }

    fun setFontSize(context: Context, size: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_FONT_SIZE, size).apply()
    }

    fun getFontName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FONT_NAME, "serif") ?: "serif"
    }

    fun setFontName(context: Context, font: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FONT_NAME, font).apply()
    }

    fun getParagraphMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PARAGRAPH_MODE, true)
    }

    fun setParagraphMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PARAGRAPH_MODE, enabled).apply()
    }

    // ---------------- Theme ----------------
    fun getCurrentTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CURRENT_THEME, "Light") ?: "Light"
    }

    fun setCurrentTheme(context: Context, themeName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CURRENT_THEME, themeName).apply()
    }

    fun getCustomThemes(context: Context): List<AppTheme> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_THEMES, null) ?: return emptyList()
        return try {
            Gson().fromJson(json, Array<AppTheme>::class.java).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomThemes(context: Context, themes: List<AppTheme>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(themes)
        prefs.edit().putString(KEY_CUSTOM_THEMES, json).apply()
    }

    // ---------------- Unified theme access ----------------
    private fun getAllThemes(context: Context): List<AppTheme> {
        return prebuiltThemes + getCustomThemes(context)
    }

    fun getThemeObject(context: Context): AppTheme {
        val themeName = getCurrentTheme(context)
        return getAllThemes(context).firstOrNull { it.name == themeName } ?: prebuiltThemes.first()
    }

    fun getThemeBackground(context: Context): Int = getThemeObject(context).backgroundColor
    fun getThemeTextColor(context: Context): Int = getThemeObject(context).textColor
    fun getThemeRedLetterColor(context: Context): Int = getThemeObject(context).redLetterColor
    // fun getThemeSecondaryBackground(context: Context): Int = getThemeObject(context).secondaryBackground not functioning
}
