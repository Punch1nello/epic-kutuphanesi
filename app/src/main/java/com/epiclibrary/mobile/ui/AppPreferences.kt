package com.epiclibrary.mobile.ui

import android.content.Context

enum class AppLanguage(val nativeName: String, val localeTag: String) {
    TURKISH("Türkçe", "tr-TR"),
    ENGLISH("English", "en-US"),
    GERMAN("Deutsch", "de-DE"),
    SPANISH("Español", "es-ES"),
    RUSSIAN("Русский", "ru-RU")
}

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun language(): AppLanguage = runCatching {
        AppLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: AppLanguage.TURKISH.name)
    }.getOrDefault(AppLanguage.TURKISH)

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    fun theme(): AppThemeMode = runCatching {
        AppThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: AppThemeMode.SYSTEM.name)
    }.getOrDefault(AppThemeMode.SYSTEM)

    fun setTheme(theme: AppThemeMode) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
    }
}
