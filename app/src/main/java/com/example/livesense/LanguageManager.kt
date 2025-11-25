package com.example.livesense

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object LanguageManager {

    private const val PREF_NAME = "AppPrefs"
    private const val KEY_LANG = "Language"

    // Set the language (e.g., "hi" for Hindi)
    fun setLocale(context: Context, langCode: String) {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)

        // Update the app's configuration
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        // Save the choice so it remembers next time
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()

        // CRITICAL: Immediately update the YOLO labels to the new language
        Constants.updateLabels(context)
    }

    // Load the saved language when app starts
    fun loadLocale(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val langCode = prefs.getString(KEY_LANG, "en") ?: "en" // Default to English
        setLocale(context, langCode)
    }
}