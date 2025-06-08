package com.example.mentalworkloadapp.util

import android.content.Context
import java.util.Locale

object LanguageUtil {

    private const val PREFS_NAME = "settings"
    private const val PREF_LANG = "lang"

    fun setLocale(context: Context, languageCode: String): Context {
        saveLanguagePreference(context, languageCode)

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun saveLanguagePreference(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_LANG, languageCode).apply()
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_LANG, "en") ?: "en"
    }
}

