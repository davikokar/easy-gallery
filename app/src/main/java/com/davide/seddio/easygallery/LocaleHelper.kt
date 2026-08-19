package com.davide.seddio.easygallery

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
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

    /**
     * Returns a context configured with the persisted language, or with the real system language
     * if following system (tag is empty). Always returns a freshly wrapped context so that
     * [Locale.setDefault] is reliably reset even when switching back to "follow system" after a
     * previous override.
     *
     * Deliberately does NOT use the deprecated [android.content.res.Resources.updateConfiguration]
     * API: it mutates a cached Resources instance in place, and the framework is free to replace
     * that instance at any time (memory trims, other configuration changes, etc.), which caused the
     * language change to intermittently "not stick". [android.content.Context.createConfigurationContext]
     * always returns a fresh Resources instance tied to the returned context, which is reliable.
     */
    fun wrap(context: Context): Context {
        val tag = getPersistedLanguageTag(context)
        if (tag.isEmpty()) {
            // Reset the JVM-wide default locale back to the real system one, in case a previous
            // in-process language override changed it (Locale.setDefault is a static, process-wide
            // field, so without this "follow system" would keep showing the last override, e.g.
            // date formatting elsewhere that reads Locale.getDefault()).
            systemLocale()?.let { Locale.setDefault(it) }
            return context
        }

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /** The real device locale, unaffected by any previous [Locale.setDefault] override. */
    private fun systemLocale(): Locale? = try {
        Resources.getSystem().configuration.locales[0]
    } catch (e: Throwable) {
        // Resources.getSystem() isn't available in plain (non-instrumented) unit tests.
        null
    }
}
