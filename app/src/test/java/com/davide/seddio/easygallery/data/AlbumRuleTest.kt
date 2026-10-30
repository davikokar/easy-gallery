package com.davide.seddio.easygallery.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AlbumRuleTest {

    private val timeClause = """{"type":"time","from":"2025-01-01","to":"2025-12-31"}"""

    private fun envelope(clauses: String, version: String = "1"): String {
        return """{"version":$version,"all":[$clauses]}"""
    }

    private fun assertUnevaluatable(raw: String, reason: UnevaluatableReason) {
        val result = AlbumRuleJson.parse(raw)
        if (result !is AlbumRule.Unevaluatable) {
            throw AssertionError("Expected Unevaluatable for <${raw.take(80)}> but was $result")
        }
        assertEquals("raw must be the input, byte for byte", raw, result.raw)
        assertEquals(reason, result.reason)
    }

    private fun evaluatable(raw: String): AlbumRule.Evaluatable {
        val result = AlbumRuleJson.parse(raw)
        if (result !is AlbumRule.Evaluatable) {
            throw AssertionError("Expected Evaluatable for <${raw.take(80)}> but was $result")
        }
        return result
    }

    private fun time(from: String?, to: String?): AlbumRuleClause.Time {
        return AlbumRuleClause.Time(from?.let(LocalDate::parse), to?.let(LocalDate::parse))
    }

    // --- NULL and text that says nothing ---

    @Test
    fun `null column parses to no rule at all`() {
        assertNull(AlbumRuleJson.parse(null))
    }

    @Test
    fun `empty string is unevaluatable and not manual`() {
        assertUnevaluatable("", UnevaluatableReason.BLANK_OR_NULL_TEXT)
    }

    @Test
    fun `whitespace only is unevaluatable and not manual`() {
        assertUnevaluatable("  \n\t ", UnevaluatableReason.BLANK_OR_NULL_TEXT)
    }

    @Test
    fun `the text null is unevaluatable and not manual`() {
        assertUnevaluatable("null", UnevaluatableReason.BLANK_OR_NULL_TEXT)
    }

    @Test
    fun `the text null with surrounding whitespace is unevaluatable and not manual`() {
        assertUnevaluatable(" null\n", UnevaluatableReason.BLANK_OR_NULL_TEXT)
    }

    // --- The three cases the ADR names, one test each ---

    @Test
    fun `unknown clause type is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"people","ids":[1,2]}"""),
            UnevaluatableReason.UNKNOWN_CLAUSE_TYPE
        )
    }

    @Test
    fun `an unknown clause beside a known one makes the whole rule unevaluatable`() {
        assertUnevaluatable(
            envelope("""$timeClause,{"type":"content","query":"castle"}"""),
            UnevaluatableReason.UNKNOWN_CLAUSE_TYPE
        )
    }

    @Test
    fun `envelope version 2 is unevaluatable`() {
        assertUnevaluatable(envelope(timeClause, version = "2"), UnevaluatableReason.UNKNOWN_VERSION)
    }

    @Test
    fun `envelope version 0 is unevaluatable`() {
        assertUnevaluatable(envelope(timeClause, version = "0"), UnevaluatableReason.UNKNOWN_VERSION)
    }

    @Test
    fun `missing envelope version is unevaluatable`() {
        assertUnevaluatable("""{"all":[$timeClause]}""", UnevaluatableReason.UNKNOWN_VERSION)
    }

    @Test
    fun `envelope version as the string 1 is unevaluatable`() {
        assertUnevaluatable(envelope(timeClause, version = "\"1\""), UnevaluatableReason.UNKNOWN_VERSION)
    }

    @Test
    fun `envelope version written as 1 point 0 is unevaluatable`() {
        assertUnevaluatable(envelope(timeClause, version = "1.0"), UnevaluatableReason.UNKNOWN_VERSION)
    }

    @Test
    fun `envelope version written as 1e0 is unevaluatable`() {
        assertUnevaluatable(envelope(timeClause, version = "1e0"), UnevaluatableReason.UNKNOWN_VERSION)
    }

    @Test
    fun `truncated json is unevaluatable`() {
        assertUnevaluatable(
            envelope(timeClause).dropLast(3),
            UnevaluatableReason.MALFORMED_JSON
        )
    }

    @Test
    fun `trailing garbage after the root object is unevaluatable`() {
        assertUnevaluatable(envelope(timeClause) + " xyz", UnevaluatableReason.TRAILING_CONTENT)
    }

    @Test
    fun `a second root object is unevaluatable`() {
        assertUnevaluatable(envelope(timeClause) + envelope(timeClause), UnevaluatableReason.TRAILING_CONTENT)
    }

    @Test
    fun `a root that is an array is unevaluatable`() {
        assertUnevaluatable("[$timeClause]", UnevaluatableReason.NOT_AN_OBJECT)
    }

    @Test
    fun `a root that is a string is unevaluatable`() {
        assertUnevaluatable("\"version\"", UnevaluatableReason.NOT_AN_OBJECT)
    }

    @Test
    fun `a bad escape sequence is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"ti\qme","from":"2025-01-01"}"""),
            UnevaluatableReason.MALFORMED_JSON
        )
    }

    @Test
    fun `a short unicode escape is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"ti\u00zme","from":"2025-01-01"}"""),
            UnevaluatableReason.MALFORMED_JSON
        )
    }

    @Test
    fun `a raw control character inside a string is unevaluatable`() {
        assertUnevaluatable(
            envelope("{\"type\":\"ti\nme\",\"from\":\"2025-01-01\"}"),
            UnevaluatableReason.MALFORMED_JSON
        )
    }

    @Test
    fun `a trailing comma is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"2025-01-01",}"""),
            UnevaluatableReason.MALFORMED_JSON
        )
    }

    @Test
    fun `a duplicate key in the envelope is unevaluatable`() {
        assertUnevaluatable(
            """{"version":1,"all":[$timeClause],"version":1}""",
            UnevaluatableReason.DUPLICATE_KEY
        )
    }

    @Test
    fun `a duplicate key inside a clause is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"2025-01-01","from":"2025-02-01"}"""),
            UnevaluatableReason.DUPLICATE_KEY
        )
    }

    @Test
    fun `a duplicate key spelled with an escape is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","\u0074ype":"time","from":"2025-01-01"}"""),
            UnevaluatableReason.DUPLICATE_KEY
        )
    }

    // --- Cases the amendment adds ---

    @Test
    fun `empty all array is unevaluatable and not everything`() {
        assertUnevaluatable("""{"version":1,"all":[]}""", UnevaluatableReason.EMPTY_CONJUNCTION)
    }

    @Test
    fun `missing all array is unevaluatable`() {
        assertUnevaluatable("""{"version":1}""", UnevaluatableReason.INVALID_ENVELOPE)
    }

    @Test
    fun `all that is not an array is unevaluatable`() {
        assertUnevaluatable("""{"version":1,"all":$timeClause}""", UnevaluatableReason.INVALID_ENVELOPE)
    }

    @Test
    fun `an unknown envelope key is unevaluatable`() {
        assertUnevaluatable(
            """{"version":1,"all":[$timeClause],"any":[$timeClause]}""",
            UnevaluatableReason.UNKNOWN_ENVELOPE_KEY
        )
    }

    @Test
    fun `a clause that is not an object is unevaluatable`() {
        assertUnevaluatable(envelope("\"time\""), UnevaluatableReason.INVALID_CLAUSE)
    }

    @Test
    fun `a clause without a type is unevaluatable`() {
        assertUnevaluatable(envelope("""{"from":"2025-01-01"}"""), UnevaluatableReason.INVALID_CLAUSE)
    }

    @Test
    fun `an inverted range is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"2025-12-31","to":"2025-01-01"}"""),
            UnevaluatableReason.INVERTED_RANGE
        )
    }

    @Test
    fun `a date that does not exist is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"2025-02-30"}"""),
            UnevaluatableReason.INVALID_DATE
        )
    }

    @Test
    fun `29 February in a non leap year is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","to":"2025-02-29"}"""),
            UnevaluatableReason.INVALID_DATE
        )
    }

    @Test
    fun `a date without a four digit year is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"25-01-01"}"""),
            UnevaluatableReason.INVALID_DATE
        )
    }

    @Test
    fun `a date with unpadded month and day is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"2025-1-1"}"""),
            UnevaluatableReason.INVALID_DATE
        )
    }

    @Test
    fun `a date carrying a time of day is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"2025-01-01T00:00:00"}"""),
            UnevaluatableReason.INVALID_DATE
        )
    }

    @Test
    fun `a bound that is not a string is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":20250101}"""),
            UnevaluatableReason.INVALID_DATE
        )
    }

    @Test
    fun `a bound that is json null is unevaluatable and not open`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":null,"to":"2025-12-31"}"""),
            UnevaluatableReason.INVALID_DATE
        )
    }

    @Test
    fun `an extra field inside a time clause is unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"time","from":"2025-01-01","tz":"Europe/Rome"}"""),
            UnevaluatableReason.UNKNOWN_CLAUSE_FIELD
        )
    }

    @Test
    fun `a region clause is unevaluatable until it is implemented`() {
        assertUnevaluatable(
            envelope("""{"type":"region","country":"IT"}"""),
            UnevaluatableReason.UNKNOWN_CLAUSE_TYPE
        )
    }

    @Test
    fun `a time clause with neither bound is unevaluatable and not everything`() {
        assertUnevaluatable(envelope("""{"type":"time"}"""), UnevaluatableReason.NO_TIME_BOUND)
    }

    // --- Caps ---

    @Test
    fun `nesting one level over the cap is unevaluatable`() {
        val depth = AlbumRuleJson.MAX_DEPTH + 1
        assertUnevaluatable("[".repeat(depth) + "]".repeat(depth), UnevaluatableReason.TOO_DEEP)
    }

    @Test
    fun `nesting exactly at the cap is not rejected for depth`() {
        val depth = AlbumRuleJson.MAX_DEPTH
        assertUnevaluatable("[".repeat(depth) + "]".repeat(depth), UnevaluatableReason.NOT_AN_OBJECT)
    }

    @Test
    fun `an unterminated depth bomb of arrays is unevaluatable without overflowing the stack`() {
        assertUnevaluatable("[".repeat(10_000), UnevaluatableReason.TOO_DEEP)
    }

    @Test
    fun `a depth bomb of objects nested inside a clause is unevaluatable without overflowing the stack`() {
        val bomb = envelope("""{"type":"time","from":"2025-01-01","x":""" + """{"a":""".repeat(3_000))

        assertUnevaluatable(bomb, UnevaluatableReason.TOO_DEEP)
    }

    @Test
    fun `a document one char over the length cap is unevaluatable`() {
        val padded = envelope(timeClause).padEnd(AlbumRuleJson.MAX_DOCUMENT_LENGTH + 1, ' ')

        assertUnevaluatable(padded, UnevaluatableReason.TOO_LONG)
    }

    @Test
    fun `an oversized string of garbage is unevaluatable`() {
        assertUnevaluatable("x".repeat(1_000_000), UnevaluatableReason.TOO_LONG)
    }

    @Test
    fun `a valid document exactly at the length cap is still evaluatable`() {
        val padded = envelope(timeClause).padEnd(AlbumRuleJson.MAX_DOCUMENT_LENGTH, ' ')

        assertNotNull(evaluatable(padded))
    }

    // --- What is evaluatable ---

    @Test
    fun `a time clause with both bounds parses`() {
        val rule = evaluatable(envelope(timeClause))

        assertEquals(listOf(time("2025-01-01", "2025-12-31")), rule.clauses)
    }

    @Test
    fun `a time clause with only from is open towards the future`() {
        val rule = evaluatable(envelope("""{"type":"time","from":"2025-03-01"}"""))

        assertEquals(listOf(time("2025-03-01", null)), rule.clauses)
    }

    @Test
    fun `a time clause with only to is open towards the past`() {
        val rule = evaluatable(envelope("""{"type":"time","to":"2025-03-31"}"""))

        assertEquals(listOf(time(null, "2025-03-31")), rule.clauses)
    }

    @Test
    fun `a single day range with from equal to to parses`() {
        val rule = evaluatable(envelope("""{"type":"time","from":"2025-07-04","to":"2025-07-04"}"""))

        assertEquals(listOf(time("2025-07-04", "2025-07-04")), rule.clauses)
    }

    @Test
    fun `29 February in a leap year parses`() {
        val rule = evaluatable(envelope("""{"type":"time","from":"2024-02-29","to":"2024-02-29"}"""))

        assertEquals(listOf(time("2024-02-29", "2024-02-29")), rule.clauses)
    }

    @Test
    fun `two time clauses are kept in order`() {
        val rule = evaluatable(
            envelope(
                """{"type":"time","from":"2020-01-01"},{"type":"time","to":"2025-12-31"}"""
            )
        )

        assertEquals(listOf(time("2020-01-01", null), time(null, "2025-12-31")), rule.clauses)
    }

    @Test
    fun `key order and insignificant whitespace do not matter`() {
        val raw = """
            {
              "all": [ { "to": "2025-12-31", "type": "time", "from": "2025-01-01" } ],
              "version": 1
            }
        """.trimIndent()

        assertEquals(listOf(time("2025-01-01", "2025-12-31")), evaluatable(raw).clauses)
    }

    @Test
    fun `an unevaluatable result carries the input exactly including odd whitespace and non ascii`() {
        val raw = " \n{\"version\":2,\"note\":\"\u00e9\u2713\"}\t "

        assertUnevaluatable(raw, UnevaluatableReason.UNKNOWN_VERSION)
    }

    // --- encode ---

    @Test
    fun `encode writes the canonical document`() {
        val rule = AlbumRule.Evaluatable(listOf(time("2025-01-01", "2025-12-31")))

        assertEquals(envelope(timeClause), AlbumRuleJson.encode(rule))
    }

    @Test
    fun `encode omits an open bound`() {
        val rule = AlbumRule.Evaluatable(listOf(time("2025-03-01", null), time(null, "2025-03-31")))

        assertEquals(
            envelope("""{"type":"time","from":"2025-03-01"},{"type":"time","to":"2025-03-31"}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode then parse round trips a range`() {
        val rule = AlbumRule.Evaluatable(listOf(time("2025-01-01", "2025-12-31")))

        assertEquals(rule, AlbumRuleJson.parse(AlbumRuleJson.encode(rule)))
    }

    @Test
    fun `encode then parse round trips open bounds and several clauses`() {
        val rule = AlbumRule.Evaluatable(
            listOf(time("2020-01-01", null), time(null, "2025-12-31"), time("2024-02-29", "2024-02-29"))
        )

        assertEquals(rule, AlbumRuleJson.parse(AlbumRuleJson.encode(rule)))
    }

    @Test
    fun `encode then parse round trips the extreme supported years`() {
        val rule = AlbumRule.Evaluatable(listOf(time("0000-01-01", "9999-12-31")))

        assertEquals(rule, AlbumRuleJson.parse(AlbumRuleJson.encode(rule)))
    }

    @Test
    fun `encode refuses a rule that would exceed the length cap`() {
        val rule = AlbumRule.Evaluatable(List(2_000) { time("2025-01-01", null) })

        assertThrows(IllegalArgumentException::class.java) { AlbumRuleJson.encode(rule) }
    }

    // --- Construction invariants: an invalid rule cannot be built, so it cannot be encoded ---

    @Test
    fun `an evaluatable rule cannot be empty`() {
        assertThrows(IllegalArgumentException::class.java) { AlbumRule.Evaluatable(emptyList()) }
    }

    @Test
    fun `a time clause cannot have neither bound`() {
        assertThrows(IllegalArgumentException::class.java) { AlbumRuleClause.Time(null, null) }
    }

    @Test
    fun `a time clause cannot be inverted`() {
        assertThrows(IllegalArgumentException::class.java) { time("2025-12-31", "2025-01-01") }
    }

    @Test
    fun `a time clause cannot use a year the stored form cannot express`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Time(LocalDate.of(10_000, 1, 1), null)
        }
    }

    // --- Totality ---

    @Test
    fun `every strict prefix of a valid document is unevaluatable`() {
        val document = envelope(timeClause)

        for (length in 0 until document.length) {
            val prefix = document.substring(0, length)
            val result = AlbumRuleJson.parse(prefix)
            if (result !is AlbumRule.Unevaluatable) {
                throw AssertionError("Prefix of length $length parsed as $result")
            }
            assertEquals(prefix, result.raw)
        }
    }

    @Test
    fun `parse never throws or hits the safety net when a valid document is corrupted one char at a time`() {
        val document = envelope(timeClause)
        val replacements = listOf('"', '\\', '{', '}', '[', ']', ',', ':', '0', '-', '\u0000')
        val mutations = document.indices.flatMap { index ->
            listOf(document.removeRange(index, index + 1)) +
                replacements.map { document.replaceRange(index, index + 1, it.toString()) }
        }

        for (mutated in mutations) {
            val result = AlbumRuleJson.parse(mutated)
            assertNotNull(result)
            if (result is AlbumRule.Unevaluatable) {
                assertEquals(mutated, result.raw)
                assertNotEquals(UnevaluatableReason.UNEXPECTED_ERROR, result.reason)
            }
        }
    }
}
