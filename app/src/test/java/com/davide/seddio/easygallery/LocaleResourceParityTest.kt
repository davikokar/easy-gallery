package com.davide.seddio.easygallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

// Read from the module directory, which Gradle uses as the working directory for unit tests.
private val RES_DIR = File("src/main/res")

private const val BASE_FOLDER = "values"

// Locales that shipped before support_email_subject and error_no_email_app were added.
private val PRE_EXISTING_FOLDERS = setOf(
    "values-ar", "values-de", "values-es", "values-fr", "values-it",
    "values-ja", "values-ko", "values-pt-rBR", "values-zh-rCN"
)
private val MISSING_IN_PRE_EXISTING = setOf("support_email_subject", "error_no_email_app")

// Integer plural categories per locale (CLDR). Existing files carry no "many" for fr/es/it/pt, so none is required.
private val PLURAL_CATEGORIES = mapOf(
    "values-ar" to setOf("zero", "one", "two", "few", "many", "other"),
    "values-de" to setOf("one", "other"),
    "values-es" to setOf("one", "other"),
    "values-fr" to setOf("one", "other"),
    "values-in" to setOf("other"),
    "values-it" to setOf("one", "other"),
    "values-ja" to setOf("other"),
    "values-ko" to setOf("other"),
    "values-pl" to setOf("one", "few", "many", "other"),
    "values-pt-rBR" to setOf("one", "other"),
    "values-ro" to setOf("one", "few", "other"),
    "values-th" to setOf("other"),
    "values-tr" to setOf("one", "other"),
    "values-uk" to setOf("one", "few", "many", "other"),
    "values-vi" to setOf("other"),
    "values-zh-rCN" to setOf("other")
)

// Arabic spells out 0, 1 and 2 in words, so those forms legitimately carry no number.
private val ARABIC_WORDED_CATEGORIES = setOf("zero", "one", "two")

private val PLACEHOLDER = Regex("%(?:\\d+\\$)?[a-zA-Z]")
private val UNESCAPED_APOSTROPHE = Regex("(?<!\\\\)'")

// Android rewrites these legacy codes before resource lookup, so a folder must use the legacy spelling.
private val LEGACY_LANGUAGE_CODES = mapOf("id" to "in", "he" to "iw", "yi" to "ji")

private class ParsedStrings(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
    val nonTranslatable: Set<String>
)

private fun parseStrings(folder: String): ParsedStrings {
    val file = File(RES_DIR, "$folder/strings.xml")
    assertTrue("missing ${file.absolutePath}", file.exists())
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    val root = factory.newDocumentBuilder().parse(file).documentElement

    val strings = linkedMapOf<String, String>()
    val plurals = linkedMapOf<String, Map<String, String>>()
    val nonTranslatable = linkedSetOf<String>()
    val children = root.childNodes
    for (index in 0 until children.length) {
        val element = children.item(index) as? Element ?: continue
        val name = element.getAttribute("name")
        when (element.tagName) {
            "string" -> {
                assertTrue("$folder: duplicate string $name", name !in strings)
                strings[name] = element.textContent
                if (element.getAttribute("translatable") == "false") nonTranslatable += name
            }
            "plurals" -> {
                assertTrue("$folder: duplicate plurals $name", name !in plurals)
                val items = element.getElementsByTagName("item")
                plurals[name] = (0 until items.length)
                    .map { items.item(it) as Element }
                    .associate { it.getAttribute("quantity") to it.textContent }
            }
        }
    }
    return ParsedStrings(strings, plurals, nonTranslatable)
}

private fun localeFolders(): List<String> =
    RES_DIR.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }!!
        .filter { File(it, "strings.xml").exists() }
        .map { it.name }
        .sorted()

private fun resourceFolderForTag(tag: String): String {
    val parts = tag.split('-')
    val language = LEGACY_LANGUAGE_CODES[parts[0]] ?: parts[0]
    val region = parts.getOrNull(1)?.let { "-r$it" }.orEmpty()
    return "values-$language$region"
}

// Normalised through toLanguageTag(): Locale.language reads "in" or "id" depending on the JDK.
private fun tagForFolder(folder: String): String {
    val qualifier = folder.removePrefix("values-").replace("-r", "-")
    return Locale.forLanguageTag(qualifier).toLanguageTag()
}

private fun placeholders(text: String): Set<String> = PLACEHOLDER.findAll(text).map { it.value }.toSet()

private fun assertNoProblems(problems: List<String>) {
    assertTrue("${problems.size} problem(s):\n" + problems.joinToString("\n"), problems.isEmpty())
}

class LocaleResourceParityTest {

    private val base = parseStrings(BASE_FOLDER)
    private val baseTranslatableStrings = base.strings.keys - base.nonTranslatable

    @Test
    fun `every supported language except English has a values folder with strings`() {
        val folders = localeFolders().toSet()

        val problems = LocaleHelper.supportedLanguageTags.filter { it != "en" }
            .map { it to resourceFolderForTag(it) }
            .filter { (_, folder) -> folder !in folders }
            .map { (tag, folder) -> "tag $tag has no $folder/strings.xml" }

        assertNoProblems(problems)
    }

    @Test
    fun `every values folder with strings belongs to a supported language`() {
        val supported = LocaleHelper.supportedLanguageTags.map { Locale.forLanguageTag(it).toLanguageTag() }.toSet()

        val problems = localeFolders()
            .filter { tagForFolder(it) !in supported }
            .map { "$it has no entry in LocaleHelper.supportedLanguageTags" }

        assertNoProblems(problems)
    }

    @Test
    fun `no resource folder uses a modern language code android never selects`() {
        val folders = localeFolders()

        val problems = LEGACY_LANGUAGE_CODES.keys
            .map { "values-$it" }
            .filter { it in folders }
            .map { "$it is never selected; Android rewrites ${it.removePrefix("values-")} to ${LEGACY_LANGUAGE_CODES.getValue(it.removePrefix("values-"))}" }

        assertNoProblems(problems)
    }

    @Test
    fun `Indonesian is stored as id and resolved from values-in`() {
        assertTrue("id" in LocaleHelper.supportedLanguageTags)
        assertEquals("values-in", resourceFolderForTag("id"))
        assertEquals("id", tagForFolder("values-in"))
        assertTrue("values-in" in localeFolders())
    }

    @Test
    fun `every locale has the same translatable string and plural keys as the base`() {
        val basePluralKeys = base.plurals.keys

        val problems = localeFolders().flatMap { folder ->
            val locale = parseStrings(folder)
            val allowedMissing = if (folder in PRE_EXISTING_FOLDERS) MISSING_IN_PRE_EXISTING else emptySet()
            val localeStringKeys = locale.strings.keys - locale.nonTranslatable - base.nonTranslatable
            listOf(
                (baseTranslatableStrings - localeStringKeys - allowedMissing).map { "$folder: missing string $it" },
                (localeStringKeys - baseTranslatableStrings).map { "$folder: extra string $it" },
                (basePluralKeys - locale.plurals.keys).map { "$folder: missing plurals $it" },
                (locale.plurals.keys - basePluralKeys).map { "$folder: extra plurals $it" }
            ).flatten()
        }

        assertNoProblems(problems)
    }

    @Test
    fun `the locales added after the first nine carry the support email strings`() {
        val problems = localeFolders()
            .filter { it !in PRE_EXISTING_FOLDERS }
            .flatMap { folder ->
                val keys = parseStrings(folder).strings.keys
                MISSING_IN_PRE_EXISTING.filter { it !in keys }.map { "$folder: missing string $it" }
            }

        assertNoProblems(problems)
    }

    @Test
    fun `every plurals element has exactly its locale's CLDR categories`() {
        val problems = localeFolders().flatMap { folder ->
            val expected = PLURAL_CATEGORIES[folder]
                ?: return@flatMap listOf("$folder: no expected plural categories; add them to PLURAL_CATEGORIES")
            parseStrings(folder).plurals
                .filter { (_, items) -> items.keys != expected }
                .map { (name, items) -> "$folder: plurals $name has ${items.keys.sorted()}, expected ${expected.sorted()}" }
        }

        assertNoProblems(problems)
    }

    @Test
    fun `every string uses the same placeholders as the base`() {
        val problems = localeFolders().flatMap { folder ->
            parseStrings(folder).strings
                .filterKeys { it in baseTranslatableStrings }
                .filter { (key, text) -> placeholders(text) != placeholders(base.strings.getValue(key)) }
                .map { (key, text) ->
                    "$folder: $key uses ${placeholders(text).sorted()}, base uses ${placeholders(base.strings.getValue(key)).sorted()}"
                }
        }

        assertNoProblems(problems)
    }

    @Test
    fun `every plurals element uses the same placeholders as the base across its items`() {
        val problems = localeFolders().flatMap { folder ->
            parseStrings(folder).plurals
                .filterKeys { it in base.plurals }
                .map { (name, items) -> Triple(name, items.values.flatMap { placeholders(it) }.toSet(), base.plurals.getValue(name).values.flatMap { placeholders(it) }.toSet()) }
                .filter { (_, actual, expected) -> actual != expected }
                .map { (name, actual, expected) -> "$folder: plurals $name uses ${actual.sorted()}, base uses ${expected.sorted()}" }
        }

        assertNoProblems(problems)
    }

    @Test
    fun `every plural item shows the count`() {
        val problems = localeFolders().flatMap { folder ->
            parseStrings(folder).plurals.flatMap { (name, items) ->
                items
                    .filter { (quantity, _) -> !(folder == "values-ar" && quantity in ARABIC_WORDED_CATEGORIES) }
                    .filter { (_, text) -> !text.contains("%1\$d") }
                    .map { (quantity, _) -> "$folder: plurals $name item $quantity has no %1\$d" }
            }
        }

        assertNoProblems(problems)
    }

    @Test
    fun `no string contains an unescaped apostrophe`() {
        val problems = (listOf(BASE_FOLDER) + localeFolders()).flatMap { folder ->
            val parsed = parseStrings(folder)
            val texts = parsed.strings.map { (key, text) -> key to text } +
                parsed.plurals.flatMap { (name, items) -> items.map { (quantity, text) -> "$name/$quantity" to text } }
            texts
                .filter { (_, text) -> !text.trim().startsWith("\"") && UNESCAPED_APOSTROPHE.containsMatchIn(text) }
                .map { (key, _) -> "$folder: $key has an apostrophe not preceded by a backslash" }
        }

        assertNoProblems(problems)
    }
}
