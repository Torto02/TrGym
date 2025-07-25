package com.torto.trgym.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import java.util.*

object LocaleManager {
    private const val LANGUAGE_KEY = "app_language"
    private val supportedLanguages = listOf("en", "it")

    fun getStoredLanguage(context: Context): String {
        val prefs = getPreferences(context)
        val lang = prefs.getString(LANGUAGE_KEY, null)
        Log.d("LocaleManager", "Stored language: $lang")

        val resolved = if (lang != null && supportedLanguages.contains(lang)) lang else getSystemLanguage()
        Log.d("LocaleManager", "Resolved language: $resolved")
        return resolved
    }

    fun setNewLocale(context: Context, language: String) {
        getPreferences(context).edit().putString(LANGUAGE_KEY, language).apply()
    }

    fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    private fun getSystemLanguage(): String {
        val systemLang = Locale.getDefault().language
        return if (supportedLanguages.contains(systemLang)) systemLang else "en"
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
}
