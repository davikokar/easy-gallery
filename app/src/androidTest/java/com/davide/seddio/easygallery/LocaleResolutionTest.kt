package com.davide.seddio.easygallery

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocaleResolutionTest {

    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val translatedTags get() = LocaleHelper.supportedLanguageTags.filter { it != "en" }

    // Persists the tag, resolves a string through LocaleHelper.wrap, and always restores the saved language.
    private fun <T> withLanguage(tag: String, block: (Context) -> T): T {
        val saved = LocaleHelper.getPersistedLanguageTag(targetContext)
        try {
            LocaleHelper.persistLanguageTag(targetContext, tag)
            return block(LocaleHelper.wrap(targetContext))
        } finally {
            LocaleHelper.persistLanguageTag(targetContext, saved)
            // Resets Locale.setDefault, which wrap changes process-wide.
            LocaleHelper.wrap(targetContext)
        }
    }

    private fun changeLanguageLabel(tag: String): String =
        withLanguage(tag) { it.getString(R.string.settings_change_language) }

    @Test
    fun everyTranslatedLanguageResolvesItsOwnResources() {
        val english = changeLanguageLabel("en")

        val untranslated = translatedTags.filter { changeLanguageLabel(it) == english }

        assertTrue("these tags fell back to English: $untranslated", untranslated.isEmpty())
    }

    @Test
    fun indonesianResolvesFromTheLegacyInFolder() {
        val english = changeLanguageLabel("en")

        val indonesian = changeLanguageLabel("id")

        assertNotEquals(english, indonesian)
    }

    @Test
    fun savedLanguageIsRestoredAfterResolving() {
        val before = LocaleHelper.getPersistedLanguageTag(targetContext)

        changeLanguageLabel("tr")

        assertEquals(before, LocaleHelper.getPersistedLanguageTag(targetContext))
    }

    @Test
    fun everyLanguageHasAnAutonymThatIsNotItsTag() {
        val problems = LocaleHelper.supportedLanguageTags.mapNotNull { tag ->
            val locale = Locale.forLanguageTag(tag)
            val name = locale.getDisplayName(locale)
            if (name.isEmpty() || name == tag) "$tag shows '$name'" else null
        }

        assertTrue("languages without a usable name in their own locale: $problems", problems.isEmpty())
    }
}
