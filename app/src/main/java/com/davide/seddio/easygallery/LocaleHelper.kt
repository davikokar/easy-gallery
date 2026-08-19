package com.davide.seddio.easygallery

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Persists and applies a user-selected app UI language that overrides the OS language.
 *
 * An empty stored value means "follow the system language". The selected language is applied by
 * wrapping the base [Context] in [attachBaseContext] of both the Application and the Activity, so it
 * works on every supported API level (minSdk 28) regardless of the framework per-app-language APIs.
 */
object LocaleHelper {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "app_language"

    /** BCP-47 language tags the app ships translations for. Empty tag = follow the system. */
    val supportedLanguageTags: List<String> = listOf(
        "en", "de", "es", "fr", "it", "pt-BR", "ar", "ko", "ja", "zh-CN"
    )

    fun getPersistedLanguageTag(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""
    }

    fun persistLanguageTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
    }

    /** Returns a context configured with the persisted language, or [context] if following system. */
    fun wrap(context: Context): Context {
        val tag = getPersistedLanguageTag(context)
        if (tag.isEmpty()) return context

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /** Updates the configuration of [context] and its application context to the persisted language. */
    fun applyLocale(context: Context) {
        val tag = getPersistedLanguageTag(context)
        val locale = if (tag.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        context.applicationContext.resources.updateConfiguration(config, context.applicationContext.resources.displayMetrics)
    }
}
