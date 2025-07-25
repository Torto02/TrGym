package com.torto.trgym.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LocaleHelper {
    private val supportedLanguages = listOf("en", "it", "fr") // aggiungi le lingue supportate

    // Leggi la lingua del sistema e verifica se è supportata
    fun getDefaultLanguage(): String {
        val systemLang = Locale.getDefault().language
        return if (supportedLanguages.contains(systemLang)) systemLang else "en"
    }

    // Imposta la lingua scelta
    fun updateLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
