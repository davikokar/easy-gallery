package com.davide.seddio.easygallery.data

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads and writes the `albums.rule` document of ADR-0017 and its Amendments 1 and 2.
 *
 * Hand-written because `org.json` is stubbed on the plain JVM and no serialisation dependency is
 * approved. Anything this reader does not fully understand becomes [AlbumRule.Unevaluatable];
 * ambiguity is never resolved towards a wider match.
 */
object AlbumRuleJson {

    // Bounds the cost of reading a document that arrived from backup. A real rule is under 200 chars.
    const val MAX_DOCUMENT_LENGTH = 16 * 1024

    // Root object = 1, "all" = 2, clause = 3. Anything deeper is not a rule this build can read.
    const val MAX_DEPTH = 8

    private val DATE_SHAPE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val TIME_CLAUSE_KEYS = setOf("type", "from", "to")
    private val REGION_CLAUSE_KEYS = setOf("type", "dataset", "country", "admin1", "admin1Name", "place")
    private val COUNTRY_SHAPE = Regex("[A-Z]{2}")
    private val INTEGER_SHAPE = Regex("-?[0-9]+")
    private const val HEX_DIGITS = "0123456789abcdef"

    /** Returns null only for a null column, which means manual. Never throws. */
    fun parse(raw: String?): AlbumRule? {
        if (raw == null) return null

        return try {
            interpretDocument(raw)
        } catch (failure: RuleFailure) {
            AlbumRule.Unevaluatable(raw, failure.reason)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unexpected: RuntimeException) {
            AlbumRule.Unevaluatable(raw, UnevaluatableReason.UNEXPECTED_ERROR)
        }
    }

    fun encode(rule: AlbumRule.Evaluatable): String {
        val text = buildString {
            append("{\"version\":1,\"all\":[")
            rule.clauses.forEachIndexed { index, clause ->
                if (index > 0) append(',')
                when (clause) {
                    is AlbumRuleClause.Time -> {
                        append("{\"type\":\"time\"")
                        clause.from?.let { append(",\"from\":\"").append(it).append('"') }
                        clause.to?.let { append(",\"to\":\"").append(it).append('"') }
                        append('}')
                    }
                    is AlbumRuleClause.Region -> {
                        append("{\"type\":\"region\",\"dataset\":").append(clause.dataset)
                        appendStringField("country", clause.country)
                        clause.admin1?.let { appendStringField("admin1", it) }
                        clause.admin1Name?.let { appendStringField("admin1Name", it) }
                        clause.place?.let { appendStringField("place", it) }
                        append('}')
                    }
                }
            }
            append("]}")
        }
        require(text.length <= MAX_DOCUMENT_LENGTH) { "Encoded rule exceeds $MAX_DOCUMENT_LENGTH chars" }
        return text
    }

    private fun StringBuilder.appendStringField(name: String, value: String) {
        append(",\"").append(name).append("\":\"")
        appendEscaped(value)
        append('"')
    }

    // Everything outside printable ASCII becomes \uXXXX, so the stored text is pure ASCII and
    // survives any text encoding; the reader decodes each escape to one UTF-16 unit, pairs included.
    private fun StringBuilder.appendEscaped(value: String) {
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c in ' '..'~' -> append(c)
                else -> {
                    val code = c.code
                    append("\\u")
                    append(HEX_DIGITS[(code shr 12) and 0xF])
                    append(HEX_DIGITS[(code shr 8) and 0xF])
                    append(HEX_DIGITS[(code shr 4) and 0xF])
                    append(HEX_DIGITS[code and 0xF])
                }
            }
        }
    }

    private fun interpretDocument(raw: String): AlbumRule {
        if (raw.length > MAX_DOCUMENT_LENGTH) fail(UnevaluatableReason.TOO_LONG)

        // Only SQL NULL means manual; text that says nothing is still somebody's write.
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") fail(UnevaluatableReason.BLANK_OR_NULL_TEXT)

        val root = RuleJsonReader(raw).readDocument() as? RuleJsonObject
            ?: fail(UnevaluatableReason.NOT_AN_OBJECT)

        // Only the integer token 1 is version 1: not 1.0, 1e0 or "1".
        val version = root.fields["version"]
        if (version !is RuleJsonNumber || version.text != "1") fail(UnevaluatableReason.UNKNOWN_VERSION)

        // A key such as a future "any" would narrow or widen the conjunction if ignored.
        if (root.fields.keys.any { it != "version" && it != "all" }) {
            fail(UnevaluatableReason.UNKNOWN_ENVELOPE_KEY)
        }

        val all = root.fields["all"] as? RuleJsonArray ?: fail(UnevaluatableReason.INVALID_ENVELOPE)
        if (all.items.isEmpty()) fail(UnevaluatableReason.EMPTY_CONJUNCTION)

        return AlbumRule.Evaluatable(all.items.map(::interpretClause))
    }

    private fun interpretClause(value: Any): AlbumRuleClause {
        val clause = value as? RuleJsonObject ?: fail(UnevaluatableReason.INVALID_CLAUSE)
        val type = clause.fields["type"] as? String ?: fail(UnevaluatableReason.INVALID_CLAUSE)

        return when (type) {
            "time" -> interpretTimeClause(clause)
            "region" -> interpretRegionClause(clause)
            else -> fail(UnevaluatableReason.UNKNOWN_CLAUSE_TYPE)
        }
    }

    private fun interpretRegionClause(clause: RuleJsonObject): AlbumRuleClause {
        val fields = clause.fields
        if (fields.keys.any { it !in REGION_CLAUSE_KEYS }) fail(UnevaluatableReason.UNKNOWN_CLAUSE_FIELD)

        // No normalising: "it" is not "IT".
        val country = fields["country"] as? String ?: fail(UnevaluatableReason.INVALID_COUNTRY)
        if (!COUNTRY_SHAPE.matches(country)) fail(UnevaluatableReason.INVALID_COUNTRY)

        // Only an integer token counts: not 1.0, 1e0 or "1".
        val dataset = fields["dataset"] as? RuleJsonNumber
        if (dataset == null || !INTEGER_SHAPE.matches(dataset.text)) fail(UnevaluatableReason.INVALID_DATASET)

        if (("admin1" in fields) != ("admin1Name" in fields)) fail(UnevaluatableReason.INCOMPLETE_ADMIN1_PAIR)
        val admin1 = fields["admin1"]?.let(::readRegionName)
        val admin1Name = fields["admin1Name"]?.let(::readRegionName)
        val place = fields["place"]?.let(::readRegionName)

        // Last, so that UNKNOWN_DATASET means the document is otherwise well formed.
        if (dataset.text != PLACE_RESOLVER_VERSION.toString()) fail(UnevaluatableReason.UNKNOWN_DATASET)

        return AlbumRuleClause.Region(country = country, admin1 = admin1, admin1Name = admin1Name, place = place)
    }

    private fun readRegionName(value: Any): String {
        val text = value as? String ?: fail(UnevaluatableReason.INVALID_REGION_NAME)
        if (text.isEmpty()) fail(UnevaluatableReason.INVALID_REGION_NAME)
        return text
    }

    private fun interpretTimeClause(clause: RuleJsonObject): AlbumRuleClause {
        if (clause.fields.keys.any { it !in TIME_CLAUSE_KEYS }) fail(UnevaluatableReason.UNKNOWN_CLAUSE_FIELD)

        val from = clause.fields["from"]?.let(::readDate)
        val to = clause.fields["to"]?.let(::readDate)

        if (from == null && to == null) fail(UnevaluatableReason.NO_TIME_BOUND)
        if (from != null && to != null && from.isAfter(to)) fail(UnevaluatableReason.INVERTED_RANGE)

        return AlbumRuleClause.Time(from = from, to = to)
    }

    private fun readDate(value: Any): LocalDate {
        val text = value as? String ?: fail(UnevaluatableReason.INVALID_DATE)
        if (!DATE_SHAPE.matches(text)) fail(UnevaluatableReason.INVALID_DATE)

        // ISO_LOCAL_DATE resolves strictly, so 2025-02-30 is rejected rather than clamped.
        return try {
            LocalDate.parse(text)
        } catch (invalid: DateTimeParseException) {
            fail(UnevaluatableReason.INVALID_DATE)
        }
    }
}

private class RuleFailure(val reason: UnevaluatableReason) :
    RuntimeException(reason.name, null, false, false)

private fun fail(reason: UnevaluatableReason): Nothing = throw RuleFailure(reason)

// JSON null is a sentinel so that no parsed value is a Kotlin null.
private object RuleJsonNull

private class RuleJsonNumber(val text: String)

private class RuleJsonObject(val fields: Map<String, Any>)

private class RuleJsonArray(val items: List<Any>)

/** Strict RFC 8259 reader. Recursion depth is capped, so a hostile document cannot overflow the stack. */
private class RuleJsonReader(private val text: String) {

    private var pos = 0

    fun readDocument(): Any {
        skipWhitespace()
        val value = readValue(depth = 0)
        skipWhitespace()
        if (pos < text.length) fail(UnevaluatableReason.TRAILING_CONTENT)
        return value
    }

    private fun readValue(depth: Int): Any {
        return when (peek() ?: fail(UnevaluatableReason.MALFORMED_JSON)) {
            '{' -> readObject(depth + 1)
            '[' -> readArray(depth + 1)
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", RuleJsonNull)
            else -> readNumber()
        }
    }

    private fun readObject(depth: Int): Any {
        if (depth > AlbumRuleJson.MAX_DEPTH) fail(UnevaluatableReason.TOO_DEEP)
        pos++

        val fields = LinkedHashMap<String, Any>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return RuleJsonObject(fields)
        }

        while (true) {
            skipWhitespace()
            if (peek() != '"') fail(UnevaluatableReason.MALFORMED_JSON)
            val key = readString()
            skipWhitespace()
            if (consume() != ':') fail(UnevaluatableReason.MALFORMED_JSON)
            skipWhitespace()
            val value = readValue(depth)

            // Which duplicate wins is parser-dependent, so the document has no single meaning.
            if (fields.put(key, value) != null) fail(UnevaluatableReason.DUPLICATE_KEY)

            skipWhitespace()
            val separator = consume()
            if (separator == '}') return RuleJsonObject(fields)
            if (separator != ',') fail(UnevaluatableReason.MALFORMED_JSON)
        }
    }

    private fun readArray(depth: Int): Any {
        if (depth > AlbumRuleJson.MAX_DEPTH) fail(UnevaluatableReason.TOO_DEEP)
        pos++

        val items = ArrayList<Any>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return RuleJsonArray(items)
        }

        while (true) {
            skipWhitespace()
            items.add(readValue(depth))
            skipWhitespace()
            val separator = consume()
            if (separator == ']') return RuleJsonArray(items)
            if (separator != ',') fail(UnevaluatableReason.MALFORMED_JSON)
        }
    }

    private fun readString(): String {
        pos++
        val builder = StringBuilder()
        while (true) {
            val c = consume()
            when {
                c == '"' -> return builder.toString()
                c == '\\' -> builder.append(readEscape())
                c < ' ' -> fail(UnevaluatableReason.MALFORMED_JSON)
                else -> builder.append(c)
            }
        }
    }

    private fun readEscape(): Char {
        return when (consume()) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                var code = 0
                repeat(4) { code = code * 16 + hexValue(consume()) }
                code.toChar()
            }
            else -> fail(UnevaluatableReason.MALFORMED_JSON)
        }
    }

    // Character.digit also accepts non-ASCII digits, which JSON does not.
    private fun hexValue(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> fail(UnevaluatableReason.MALFORMED_JSON)
        }
    }

    private fun readLiteral(word: String, value: Any): Any {
        if (!text.startsWith(word, pos)) fail(UnevaluatableReason.MALFORMED_JSON)
        pos += word.length
        return value
    }

    private fun readNumber(): Any {
        val start = pos
        if (peek() == '-') pos++

        val first = peek() ?: fail(UnevaluatableReason.MALFORMED_JSON)
        when (first) {
            '0' -> pos++
            in '1'..'9' -> {
                pos++
                skipDigits()
            }
            else -> fail(UnevaluatableReason.MALFORMED_JSON)
        }

        if (peek() == '.') {
            pos++
            requireDigits()
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++
            if (peek() == '+' || peek() == '-') pos++
            requireDigits()
        }

        return RuleJsonNumber(text.substring(start, pos))
    }

    private fun requireDigits() {
        if (!isDigit(peek())) fail(UnevaluatableReason.MALFORMED_JSON)
        skipDigits()
    }

    private fun skipDigits() {
        while (isDigit(peek())) pos++
    }

    private fun isDigit(c: Char?): Boolean = c != null && c in '0'..'9'

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) pos++
    }

    private fun peek(): Char? = if (pos < text.length) text[pos] else null

    private fun consume(): Char {
        val c = peek() ?: fail(UnevaluatableReason.MALFORMED_JSON)
        pos++
        return c
    }
}
