package com.davide.seddio.easygallery.data

import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
            envelope("""{"type":"scene","labels":[1,2]}"""),
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

    // --- The region clause: strictness (ADR-0017 Amendment 2) ---

    private fun regionDocument(fields: String): String = envelope("""{"type":"region",$fields}""")

    private fun assertRegionUnevaluatable(fields: String, reason: UnevaluatableReason) {
        assertUnevaluatable(regionDocument(fields), reason)
    }

    private fun assertRoundTrips(vararg clauses: AlbumRuleClause) {
        val rule = AlbumRule.Evaluatable(clauses.toList())
        val encoded = AlbumRuleJson.encode(rule)

        assertEquals(encoded, rule, AlbumRuleJson.parse(encoded))
    }

    @Test
    fun `an extra field inside a region clause is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","radiusKm":50""",
            UnevaluatableReason.UNKNOWN_CLAUSE_FIELD
        )
    }

    @Test
    fun `an admin2 field is unknown and makes a region clause unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin2":"FI"""",
            UnevaluatableReason.UNKNOWN_CLAUSE_FIELD
        )
    }

    @Test
    fun `a region clause without a country is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a lower case country is unevaluatable and not normalised`() {
        assertRegionUnevaluatable(""""dataset":1,"country":"it"""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a mixed case country is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":"It"""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a one letter country is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":"I"""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a three letter country is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":"ITA"""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `an empty country is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":"" """.trim(), UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a country with a digit is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":"I1"""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a country padded with whitespace is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":" IT"""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a country with a trailing newline escape is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":"IT\n"""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a country in non ascii letters is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"\uFF29\uFF34"""",
            UnevaluatableReason.INVALID_COUNTRY
        )
    }

    @Test
    fun `a country that is a number is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":39""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a country that is json null is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1,"country":null""", UnevaluatableReason.INVALID_COUNTRY)
    }

    @Test
    fun `a region clause without a dataset is unevaluatable`() {
        assertRegionUnevaluatable(""""country":"IT"""", UnevaluatableReason.INVALID_DATASET)
    }

    @Test
    fun `a dataset that is the string 1 is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":"1","country":"IT"""", UnevaluatableReason.INVALID_DATASET)
    }

    @Test
    fun `a dataset written as 1 point 0 is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1.0,"country":"IT"""", UnevaluatableReason.INVALID_DATASET)
    }

    @Test
    fun `a dataset written as 1e0 is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":1e0,"country":"IT"""", UnevaluatableReason.INVALID_DATASET)
    }

    @Test
    fun `a dataset that is json null is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":null,"country":"IT"""", UnevaluatableReason.INVALID_DATASET)
    }

    @Test
    fun `a dataset that is a boolean is unevaluatable`() {
        assertRegionUnevaluatable(""""dataset":true,"country":"IT"""", UnevaluatableReason.INVALID_DATASET)
    }

    @Test
    fun `an admin1 without an admin1Name is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin1":"16"""",
            UnevaluatableReason.INCOMPLETE_ADMIN1_PAIR
        )
    }

    @Test
    fun `an admin1Name without an admin1 is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin1Name":"Tuscany"""",
            UnevaluatableReason.INCOMPLETE_ADMIN1_PAIR
        )
    }

    @Test
    fun `an admin1 that is not a string is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin1":16,"admin1Name":"Tuscany"""",
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `an admin1 that is json null is unevaluatable and not absent`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin1":null,"admin1Name":"Tuscany"""",
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `an empty admin1 is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin1":"","admin1Name":"Tuscany"""",
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `an admin1Name that is not a string is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin1":"16","admin1Name":["Tuscany"]""",
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `an empty admin1Name is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","admin1":"16","admin1Name":"" """.trim(),
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `a place that is not a string is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","place":42""",
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `a place that is json null is unevaluatable and not absent`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","place":null""",
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `an empty place is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","place":"" """.trim(),
            UnevaluatableReason.INVALID_REGION_NAME
        )
    }

    @Test
    fun `a dataset newer than the current one is unknown`() {
        assertRegionUnevaluatable(
            """"dataset":${PLACE_RESOLVER_VERSION + 1},"country":"IT"""",
            UnevaluatableReason.UNKNOWN_DATASET
        )
    }

    @Test
    fun `a dataset older than the current one is unknown`() {
        assertRegionUnevaluatable(""""dataset":0,"country":"IT"""", UnevaluatableReason.UNKNOWN_DATASET)
    }

    @Test
    fun `a negative dataset is unknown and not malformed`() {
        assertRegionUnevaluatable(""""dataset":-1,"country":"IT"""", UnevaluatableReason.UNKNOWN_DATASET)
    }

    @Test
    fun `a dataset too large for an int is unknown and not malformed`() {
        assertRegionUnevaluatable(
            """"dataset":99999999999999999999,"country":"IT"""",
            UnevaluatableReason.UNKNOWN_DATASET
        )
    }

    @Test
    fun `an unknown dataset keeps the whole region clause as raw text`() {
        val raw = regionDocument(""""dataset":7,"country":"IT","admin1":"16","admin1Name":"Tuscany","place":"Florence"""")

        assertUnevaluatable(raw, UnevaluatableReason.UNKNOWN_DATASET)
    }

    @Test
    fun `an unknown dataset is reported only for an otherwise well formed clause`() {
        assertRegionUnevaluatable(""""dataset":7,"country":"it"""", UnevaluatableReason.INVALID_COUNTRY)
        assertRegionUnevaluatable(
            """"dataset":7,"country":"IT","admin1":"16"""",
            UnevaluatableReason.INCOMPLETE_ADMIN1_PAIR
        )
        assertRegionUnevaluatable(
            """"dataset":7,"country":"IT","radiusKm":5""",
            UnevaluatableReason.UNKNOWN_CLAUSE_FIELD
        )
    }

    @Test
    fun `a duplicate key inside a region clause is unevaluatable`() {
        assertRegionUnevaluatable(
            """"dataset":1,"country":"IT","country":"FR"""",
            UnevaluatableReason.DUPLICATE_KEY
        )
    }

    @Test
    fun `a bad region clause beside a good time clause makes the whole rule unevaluatable and never narrower`() {
        assertUnevaluatable(
            envelope("""$timeClause,{"type":"region","dataset":1,"country":"it"}"""),
            UnevaluatableReason.INVALID_COUNTRY
        )
    }

    @Test
    fun `a region clause with an unknown dataset beside a time clause makes the whole rule unevaluatable`() {
        assertUnevaluatable(
            envelope("""{"type":"region","dataset":2,"country":"IT"},$timeClause"""),
            UnevaluatableReason.UNKNOWN_DATASET
        )
    }

    // --- The region clause: what is evaluatable ---

    @Test
    fun `a country level region clause parses`() {
        val rule = evaluatable(regionDocument(""""dataset":1,"country":"IT""""))

        assertEquals(listOf(AlbumRuleClause.Region(country = "IT")), rule.clauses)
    }

    @Test
    fun `a region level clause parses`() {
        val rule = evaluatable(
            regionDocument(""""dataset":1,"country":"IT","admin1":"16","admin1Name":"Tuscany"""")
        )

        assertEquals(
            listOf(AlbumRuleClause.Region(country = "IT", admin1 = "16", admin1Name = "Tuscany")),
            rule.clauses
        )
    }

    @Test
    fun `a place level clause with a region parses`() {
        val rule = evaluatable(
            regionDocument(
                """"dataset":1,"country":"IT","admin1":"16","admin1Name":"Tuscany","place":"Florence""""
            )
        )

        assertEquals(
            listOf(
                AlbumRuleClause.Region(
                    country = "IT",
                    admin1 = "16",
                    admin1Name = "Tuscany",
                    place = "Florence"
                )
            ),
            rule.clauses
        )
    }

    @Test
    fun `a place level clause without a region parses as a null region place`() {
        val rule = evaluatable(regionDocument(""""dataset":1,"country":"SG","place":"Singapore""""))

        assertEquals(listOf(AlbumRuleClause.Region(country = "SG", place = "Singapore")), rule.clauses)
    }

    @Test
    fun `region key order and whitespace do not matter`() {
        val raw = """{ "all": [ { "place": "Florence", "admin1Name": "Tuscany", "type": "region",
            | "country": "IT", "admin1": "16", "dataset": 1 } ], "version": 1 }""".trimMargin()

        assertEquals(
            listOf(AlbumRuleClause.Region("IT", "16", "Tuscany", "Florence")),
            evaluatable(raw).clauses
        )
    }

    @Test
    fun `escapes inside region names are decoded`() {
        val rule = evaluatable(
            regionDocument(""""dataset":1,"country":"CH","place":"Z\u00fcrich \"Old\" \\ \ud83d\ude00"""")
        )

        assertEquals(listOf(AlbumRuleClause.Region(country = "CH", place = "Z\u00fcrich \"Old\" \\ \uD83D\uDE00")), rule.clauses)
    }

    @Test
    fun `verbatim non ascii inside a region name is accepted`() {
        val rule = evaluatable(regionDocument("\"dataset\":1,\"country\":\"CH\",\"place\":\"Z\u00fcrich\""))

        assertEquals(listOf(AlbumRuleClause.Region(country = "CH", place = "Z\u00fcrich")), rule.clauses)
    }

    @Test
    fun `a time clause and a region clause combine in either order`() {
        val region = """{"type":"region","dataset":1,"country":"IT"}"""

        assertEquals(
            listOf(time("2025-01-01", "2025-12-31"), AlbumRuleClause.Region(country = "IT")),
            evaluatable(envelope("$timeClause,$region")).clauses
        )
        assertEquals(
            listOf(AlbumRuleClause.Region(country = "IT"), time("2025-01-01", "2025-12-31")),
            evaluatable(envelope("$region,$timeClause")).clauses
        )
    }

    @Test
    fun `two region clauses naming different countries are a valid rule and not malformed`() {
        val rule = evaluatable(
            envelope(
                """{"type":"region","dataset":1,"country":"IT"},{"type":"region","dataset":1,"country":"FR"}"""
            )
        )

        assertEquals(
            listOf(AlbumRuleClause.Region(country = "IT"), AlbumRuleClause.Region(country = "FR")),
            rule.clauses
        )
    }

    // --- The region clause: encode ---

    @Test
    fun `encode writes a country level region clause canonically`() {
        val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = "IT")))

        assertEquals(envelope("""{"type":"region","dataset":1,"country":"IT"}"""), AlbumRuleJson.encode(rule))
    }

    @Test
    fun `encode writes a region level clause canonically`() {
        val rule = AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Region(country = "IT", admin1 = "16", admin1Name = "Tuscany"))
        )

        assertEquals(
            envelope("""{"type":"region","dataset":1,"country":"IT","admin1":"16","admin1Name":"Tuscany"}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode writes a place level clause canonically`() {
        val rule = AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Region("IT", "16", "Tuscany", "Florence"))
        )

        assertEquals(
            envelope(
                """{"type":"region","dataset":1,"country":"IT","admin1":"16","admin1Name":"Tuscany","place":"Florence"}"""
            ),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode writes a place without a region and omits the pair`() {
        val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = "SG", place = "Singapore")))

        assertEquals(
            envelope("""{"type":"region","dataset":1,"country":"SG","place":"Singapore"}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode writes a time clause then a region clause in the order given`() {
        val rule = AlbumRule.Evaluatable(
            listOf(time("2025-01-01", "2025-12-31"), AlbumRuleClause.Region(country = "IT"))
        )

        assertEquals(
            envelope("""$timeClause,{"type":"region","dataset":1,"country":"IT"}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode escapes a double quote in a name`() {
        val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = "IT", place = "Say \"hi\"")))

        assertEquals(
            envelope("""{"type":"region","dataset":1,"country":"IT","place":"Say \"hi\""}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode escapes a backslash in a name`() {
        val rule = AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = "IT", place = "a\\b")))

        assertEquals(
            envelope("""{"type":"region","dataset":1,"country":"IT","place":"a\\b"}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode escapes control characters in a name`() {
        val rule = AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Region(country = "IT", place = "a\nb\tc\u0000d\u007fe"))
        )

        assertEquals(
            envelope("""{"type":"region","dataset":1,"country":"IT","place":"a\u000ab\u0009c\u0000d\u007fe"}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode escapes non ascii text as lower case unicode escapes including surrogate pairs`() {
        val rule = AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Region(country = "CH", place = "Z\u00fcrich \uD83D\uDE00"))
        )

        assertEquals(
            envelope("""{"type":"region","dataset":1,"country":"CH","place":"Z\u00fcrich \ud83d\ude00"}"""),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `encode escapes the admin1 code and admin1Name as well as the place`() {
        val rule = AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Region("IT", "1\"6", "Tus\\cany", "Flo\u00e8"))
        )

        assertEquals(
            envelope(
                """{"type":"region","dataset":1,"country":"IT","admin1":"1\"6","admin1Name":"Tus\\cany","place":"Flo\u00e8"}"""
            ),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `an encoded document containing a hostile name is pure printable ascii`() {
        for (name in HOSTILE_NAMES) {
            val encoded = AlbumRuleJson.encode(
                AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT", "16", name, name)))
            )

            assertTrue(name, encoded.all { it in ' '..'~' })
        }
    }

    @Test
    fun `encode refuses a region name that would exceed the length cap`() {
        val rule = AlbumRule.Evaluatable(
            listOf(AlbumRuleClause.Region(country = "IT", place = "x".repeat(AlbumRuleJson.MAX_DOCUMENT_LENGTH)))
        )

        assertThrows(IllegalArgumentException::class.java) { AlbumRuleJson.encode(rule) }
    }

    // --- The region clause: round trips ---

    @Test
    fun `a country level region clause round trips`() {
        assertRoundTrips(AlbumRuleClause.Region(country = "IT"))
    }

    @Test
    fun `a region level clause round trips`() {
        assertRoundTrips(AlbumRuleClause.Region(country = "IT", admin1 = "16", admin1Name = "Tuscany"))
    }

    @Test
    fun `a place level clause round trips`() {
        assertRoundTrips(AlbumRuleClause.Region("IT", "16", "Tuscany", "Florence"))
    }

    @Test
    fun `a place level clause without a region round trips`() {
        assertRoundTrips(AlbumRuleClause.Region(country = "SG", place = "Singapore"))
    }

    @Test
    fun `a region clause combined with a time clause round trips`() {
        assertRoundTrips(
            AlbumRuleClause.Region("IT", "16", "Tuscany", "Florence"),
            time("2025-01-01", "2025-12-31")
        )
        assertRoundTrips(
            time(null, "2025-12-31"),
            AlbumRuleClause.Region(country = "FR")
        )
    }

    @Test
    fun `several clauses of both types round trip in order`() {
        assertRoundTrips(
            time("2020-01-01", null),
            AlbumRuleClause.Region(country = "IT"),
            AlbumRuleClause.Region(country = "FR", place = "Paris"),
            time(null, "2025-12-31")
        )
    }

    @Test
    fun `hostile names round trip as a place`() {
        for (name in HOSTILE_NAMES) {
            assertRoundTrips(AlbumRuleClause.Region(country = "IT", place = name))
        }
    }

    @Test
    fun `hostile names round trip as an admin1Name`() {
        for (name in HOSTILE_NAMES) {
            assertRoundTrips(AlbumRuleClause.Region(country = "IT", admin1 = "16", admin1Name = name))
        }
    }

    @Test
    fun `hostile names round trip as an admin1 code`() {
        for (name in HOSTILE_NAMES) {
            assertRoundTrips(AlbumRuleClause.Region(country = "IT", admin1 = name, admin1Name = "Tuscany"))
        }
    }

    @Test
    fun `hostile names round trip in all three fields at once beside a time clause`() {
        for (name in HOSTILE_NAMES) {
            assertRoundTrips(
                time("2025-01-01", "2025-12-31"),
                AlbumRuleClause.Region("IT", name, name, name)
            )
        }
    }

    @Test
    fun `a name that imitates json structure stays one field`() {
        val rule = evaluatable(
            AlbumRuleJson.encode(
                AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = "IT", place = "x\",\"country\":\"FR")))
            )
        )

        assertEquals(listOf(AlbumRuleClause.Region(country = "IT", place = "x\",\"country\":\"FR")), rule.clauses)
    }

    @Test
    fun `a long name round trips`() {
        assertRoundTrips(AlbumRuleClause.Region(country = "IT", place = "\u00e8".repeat(2_000)))
    }

    // --- The region clause: construction invariants ---

    @Test
    fun `a region clause cannot have a lower case country`() {
        assertThrows(IllegalArgumentException::class.java) { AlbumRuleClause.Region(country = "it") }
    }

    @Test
    fun `a region clause cannot have a country of the wrong length`() {
        assertThrows(IllegalArgumentException::class.java) { AlbumRuleClause.Region(country = "I") }
        assertThrows(IllegalArgumentException::class.java) { AlbumRuleClause.Region(country = "ITA") }
        assertThrows(IllegalArgumentException::class.java) { AlbumRuleClause.Region(country = "") }
    }

    @Test
    fun `a region clause cannot have a non ascii country`() {
        assertThrows(IllegalArgumentException::class.java) { AlbumRuleClause.Region(country = "\u00c0B") }
    }

    @Test
    fun `a region clause cannot have an admin1 without an admin1Name`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Region(country = "IT", admin1 = "16")
        }
    }

    @Test
    fun `a region clause cannot have an admin1Name without an admin1`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Region(country = "IT", admin1Name = "Tuscany")
        }
    }

    @Test
    fun `a region clause cannot have an empty name`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Region(country = "IT", admin1 = "", admin1Name = "Tuscany")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Region(country = "IT", admin1 = "16", admin1Name = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Region(country = "IT", place = "")
        }
    }

    @Test
    fun `a region clause cannot carry a dataset other than the current one`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Region(country = "IT", dataset = PLACE_RESOLVER_VERSION + 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlbumRuleClause.Region(country = "IT", dataset = PLACE_RESOLVER_VERSION - 1)
        }
    }

    @Test
    fun `copying a region clause into an invalid one is refused`() {
        val valid = AlbumRuleClause.Region(country = "IT", admin1 = "16", admin1Name = "Tuscany")

        assertThrows(IllegalArgumentException::class.java) { valid.copy(admin1Name = null) }
    }

    // --- The dataset pin ---

    @Test
    fun `the place resolver version is pinned at 1`() {
        assertEquals(
            "Bumping PLACE_RESOLVER_VERSION requires the region clause remap ADR-0017 Amendment 2 demands; ship it and delete this test",
            1,
            PLACE_RESOLVER_VERSION
        )
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

    @Test
    fun `every strict prefix of a valid region document is unevaluatable`() {
        val document = AlbumRuleJson.encode(
            AlbumRule.Evaluatable(
                listOf(time("2025-01-01", null), AlbumRuleClause.Region("IT", "16", "T\u00fcscany \"x\"", "Florence"))
            )
        )

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
    fun `parse never throws or hits the safety net when a valid region document is corrupted one char at a time`() {
        val document = AlbumRuleJson.encode(
            AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region("IT", "16", "T\u00fcscany", "Florence")))
        )
        val replacements = listOf('"', '\\', '{', '}', '[', ']', ',', ':', '0', '-', '\u0000', 'u', 'e', '.')
        val mutations = document.indices.flatMap { index ->
            listOf(document.removeRange(index, index + 1)) +
                replacements.map { document.replaceRange(index, index + 1, it.toString()) }
        }

        for (mutated in mutations) {
            val result = AlbumRuleJson.parse(mutated)
            assertNotNull(result)
            if (result is AlbumRule.Unevaluatable) {
                assertEquals(mutated, result.raw)
                assertNotEquals(mutated, UnevaluatableReason.UNEXPECTED_ERROR, result.reason)
            }
        }
    }

    // --- The people clause (ADR-0017 Amendment 4; stage 3 step 3.5) ---

    private val anchorUri = "content://media/external/images/media/1234"

    private val goldenPersonText =
        """{"type":"people","pipeline":1,"uri":"content://media/external/images/media/1234",""" +
            """"displayName":"IMG_0001.jpg","size":2345678,"dateModified":1700000000,""" +
            """"box":{"left":0.25,"top":0.125,"right":0.5,"bottom":0.75}}"""

    private val validClauseFields: Map<String, String> = linkedMapOf(
        "type" to "\"people\"",
        "pipeline" to "1",
        "uri" to "\"$anchorUri\"",
        "displayName" to "\"IMG_0001.jpg\"",
        "size" to "2345678",
        "dateModified" to "1700000000",
        "box" to """{"left":0.25,"top":0.125,"right":0.5,"bottom":0.75}"""
    )

    private val validBoxFields: Map<String, String> =
        linkedMapOf("left" to "0.25", "top" to "0.125", "right" to "0.5", "bottom" to "0.75")

    private fun person(
        uri: String = anchorUri,
        displayName: String = "IMG_0001.jpg",
        size: Long = 2_345_678L,
        dateModified: Long = 1_700_000_000L,
        box: FaceBox = FaceBox(0.25f, 0.125f, 0.5f, 0.75f),
        pipelineVersion: Int = 1
    ) = AlbumRuleClause.People(mediaIdentityOf(uri, displayName, size, dateModified), box, pipelineVersion)

    // A null value removes the field; a name the base lacks is appended.
    private fun objectText(base: Map<String, String>, changes: Array<out Pair<String, String?>>): String {
        val fields = LinkedHashMap(base)
        for ((name, value) in changes) {
            if (value == null) fields.remove(name) else fields[name] = value
        }
        return fields.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }
    }

    private fun boxText(vararg changes: Pair<String, String?>) = objectText(validBoxFields, changes)

    private fun peopleDocument(vararg changes: Pair<String, String?>) =
        envelope(objectText(validClauseFields, changes))

    private fun boxDocument(vararg changes: Pair<String, String?>) = peopleDocument("box" to boxText(*changes))

    private fun assertRejected(label: String, document: String, reason: UnevaluatableReason) {
        try {
            assertUnevaluatable(document, reason)
        } catch (failure: AssertionError) {
            throw AssertionError("$label: ${failure.message}", failure)
        }
    }

    // A null value is the field missing.
    private fun assertClauseValuesRejected(field: String, reason: UnevaluatableReason, vararg values: String?) {
        for (value in values) assertRejected("$field=$value", peopleDocument(field to value), reason)
    }

    private fun assertBoxValuesRejected(field: String, reason: UnevaluatableReason, vararg values: String?) {
        for (value in values) assertRejected("box.$field=$value", boxDocument(field to value), reason)
    }

    private fun assertSameBoxBits(expected: FaceBox, actual: FaceBox) {
        assertEquals(expected.left.toRawBits(), actual.left.toRawBits())
        assertEquals(expected.top.toRawBits(), actual.top.toRawBits())
        assertEquals(expected.right.toRawBits(), actual.right.toRawBits())
        assertEquals(expected.bottom.toRawBits(), actual.bottom.toRawBits())
    }

    @Test
    fun `the valid people document the strictness tests start from parses`() {
        val rule = evaluatable(peopleDocument())

        assertEquals(listOf(person()), rule.clauses)
        assertEquals(envelope(goldenPersonText), peopleDocument())
    }

    // --- people: encoding and round trips ---

    @Test
    fun `a people clause encodes to the exact stored text in the documented key order`() {
        val encoded = AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(person())))

        assertEquals(envelope(goldenPersonText), encoded)
    }

    @Test
    fun `a non ascii display name is stored as lower case unicode escapes`() {
        val encoded = AlbumRuleJson.encode(
            AlbumRule.Evaluatable(listOf(person(displayName = "Z\u00fcrich \uD83D\uDE00 \"x\" \\.jpg")))
        )

        assertEquals(
            envelope(
                """{"type":"people","pipeline":1,"uri":"content://media/external/images/media/1234",""" +
                    """"displayName":"Z\u00fcrich \ud83d\ude00 \"x\" \\.jpg","size":2345678,"dateModified":1700000000,""" +
                    """"box":{"left":0.25,"top":0.125,"right":0.5,"bottom":0.75}}"""
            ),
            encoded
        )
        assertTrue(encoded.all { it in ' '..'~' })
    }

    @Test
    fun `a people clause round trips`() {
        assertRoundTrips(person())
    }

    @Test
    fun `a people clause with a non ascii or hostile display name round trips`() {
        for (name in HOSTILE_NAMES) assertRoundTrips(person(displayName = name))
    }

    @Test
    fun `a people clause with a hostile uri round trips`() {
        for (name in HOSTILE_NAMES) assertRoundTrips(person(uri = "content://x/$name"))
    }

    @Test
    fun `a long display name round trips`() {
        assertRoundTrips(person(displayName = "\u00e8".repeat(255)))
    }

    @Test
    fun `several people clauses round trip in order`() {
        assertRoundTrips(
            person(),
            person(uri = "content://media/external/images/media/9", displayName = "b.jpg", pipelineVersion = 3),
            person(box = FaceBox(0.5f, 0.5f, 0.75f, 0.75f))
        )
    }

    @Test
    fun `people clauses mixed with region and time clauses round trip`() {
        assertRoundTrips(
            time("2025-01-01", "2025-12-31"),
            person(),
            AlbumRuleClause.Region("IT", "16", "Tuscany", "Florence"),
            person(displayName = "b.jpg", box = FaceBox(0.1f, 0.1f, 0.2f, 0.2f))
        )
    }

    @Test
    fun `the box edges zero and one round trip bit for bit`() {
        val clause = person(box = FaceBox(0f, 0f, 1f, 1f))
        val parsed = evaluatable(AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(clause))))

        assertSameBoxBits(clause.box, (parsed.clauses.single() as AlbumRuleClause.People).box)
    }

    @Test
    fun `awkward float box edges round trip bit for bit`() {
        val boxes = listOf(
            FaceBox(1f / 3f, 0.1f, 2f / 3f, 0.9f),
            FaceBox(0.1f, 0.2f, 0.3f, 0.7f),
            FaceBox(0f, 0f, Float.MIN_VALUE, Float.MIN_VALUE),
            FaceBox(Float.MIN_VALUE, Float.MIN_VALUE, 0.5f, 0.5f),
            FaceBox(0f, 0f, Math.nextDown(1f), Math.nextDown(1f)),
            FaceBox(Math.nextUp(0f), 0.4f, Math.nextDown(0.5f), Math.nextUp(0.4f))
        )

        for (box in boxes) {
            val parsed = evaluatable(AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(person(box = box)))))

            assertSameBoxBits(box, (parsed.clauses.single() as AlbumRuleClause.People).box)
        }
    }

    @Test
    fun `any box the face pass can store round trips bit for bit`() {
        val random = Random(35)
        var checked = 0
        while (checked < 500) {
            val a = random.nextFloat()
            val b = random.nextFloat()
            val c = random.nextFloat()
            val d = random.nextFloat()
            if (a == b || c == d) continue
            val box = FaceBox(minOf(a, b), minOf(c, d), maxOf(a, b), maxOf(c, d))
            val clause = person(box = box)

            val parsed = evaluatable(AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(clause))))

            assertEquals(clause, parsed.clauses.single())
            assertSameBoxBits(box, (parsed.clauses.single() as AlbumRuleClause.People).box)
            checked++
        }
    }

    @Test
    fun `a clause anchored at a picker representative round trips its box bit for bit`() {
        val representative = FaceGroupRepresentative(
            identity = mediaIdentityOf(anchorUri, "IMG_0001.jpg", 2_345_678L, 1_700_000_000L),
            box = FaceBox(0.31f, 1f / 7f, 0.77f, 0.6180339f),
            pipelineVersion = 1
        )
        val clause = AlbumRuleClause.People.anchoredAt(representative)

        val parsed = evaluatable(AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(clause))))

        assertEquals(clause, parsed.clauses.single())
        assertSameBoxBits(representative.box, (parsed.clauses.single() as AlbumRuleClause.People).box)
    }

    @Test
    fun `no default locale changes the stored text`() {
        val original = Locale.getDefault()
        try {
            for (locale in listOf(Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("fa-IR"))) {
                Locale.setDefault(locale)
                val rule = AlbumRule.Evaluatable(listOf(person(box = FaceBox(0.25f, 0.125f, 0.5f, 0.75f))))

                val encoded = AlbumRuleJson.encode(rule)

                assertEquals(locale.toLanguageTag(), envelope(goldenPersonText), encoded)
                assertEquals(locale.toLanguageTag(), rule, AlbumRuleJson.parse(encoded))
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `the largest and smallest longs round trip as the anchor triple`() {
        assertRoundTrips(person(size = Long.MAX_VALUE, dateModified = Long.MIN_VALUE))
        assertRoundTrips(person(size = 0L, dateModified = -1L))
    }

    @Test
    fun `fifty typical people clauses fit under the encoder cap`() {
        val clauses = (1..50).map { person(uri = "content://media/external/images/media/${1_000_000 + it}") }

        val encoded = AlbumRuleJson.encode(AlbumRule.Evaluatable(clauses))

        assertTrue(encoded.length <= AlbumRuleJson.MAX_DOCUMENT_LENGTH)
        assertEquals(AlbumRule.Evaluatable(clauses), AlbumRuleJson.parse(encoded))
    }

    @Test
    fun `eight people clauses with the longest escaped file names still fit and ten do not`() {
        fun rule(count: Int) = AlbumRule.Evaluatable(
            (1..count).map { person(displayName = "\u00e8".repeat(255), size = it.toLong()) }
        )

        assertTrue(AlbumRuleJson.encode(rule(8)).length <= AlbumRuleJson.MAX_DOCUMENT_LENGTH)
        assertThrows(IllegalArgumentException::class.java) { AlbumRuleJson.encode(rule(10)) }
    }

    // --- people: strict parsing ---

    @Test
    fun `an unknown key in a people clause is an unknown clause field`() {
        assertClauseValuesRejected("embedding", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "[0.1,0.2]")
        assertClauseValuesRejected("groupId", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "5")
        assertClauseValuesRejected("name", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "\"Alice\"")
        assertClauseValuesRejected("score", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "0.9")
        assertClauseValuesRejected("Uri", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "\"x\"")
    }

    @Test
    fun `an unknown key inside box is an unknown clause field`() {
        assertBoxValuesRejected("score", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "0.9")
        assertBoxValuesRejected("width", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "0.25")
        assertBoxValuesRejected("Left", UnevaluatableReason.UNKNOWN_CLAUSE_FIELD, "0.25")
    }

    @Test
    fun `a missing or unusable uri is an invalid anchor`() {
        assertClauseValuesRejected("uri", UnevaluatableReason.INVALID_ANCHOR, null, "\"\"", "5", "null", "true", "[]", "{}")
    }

    @Test
    fun `a missing or unusable display name is an invalid anchor`() {
        assertClauseValuesRejected(
            "displayName", UnevaluatableReason.INVALID_ANCHOR, null, "\"\"", "5", "null", "false", "[]", "{}"
        )
    }

    @Test
    fun `a missing or unusable size is an invalid anchor`() {
        assertClauseValuesRejected(
            "size",
            UnevaluatableReason.INVALID_ANCHOR,
            null, "\"5\"", "1.0", "1e3", "null", "true",
            "9223372036854775808", "-9223372036854775809", "99999999999999999999999"
        )
    }

    @Test
    fun `a missing or unusable dateModified is an invalid anchor`() {
        assertClauseValuesRejected(
            "dateModified",
            UnevaluatableReason.INVALID_ANCHOR,
            null, "\"5\"", "1.0", "1e3", "null", "true",
            "9223372036854775808", "-9223372036854775809", "99999999999999999999999"
        )
    }

    @Test
    fun `a missing or unusable pipeline is an invalid pipeline version`() {
        assertClauseValuesRejected(
            "pipeline",
            UnevaluatableReason.INVALID_PIPELINE_VERSION,
            null, "\"1\"", "0", "-1", "1.0", "1e0", "null", "true",
            "2147483648", "99999999999999999999"
        )
    }

    @Test
    fun `pipeline 99 and the largest int parse because the version is informational`() {
        val ninetyNine = evaluatable(peopleDocument("pipeline" to "99")).clauses.single()
        val largest = evaluatable(peopleDocument("pipeline" to Int.MAX_VALUE.toString())).clauses.single()

        assertEquals(person(pipelineVersion = 99), ninetyNine)
        assertEquals(person(pipelineVersion = Int.MAX_VALUE), largest)
    }

    @Test
    fun `a missing or unusable box is an invalid face box`() {
        assertClauseValuesRejected("box", UnevaluatableReason.INVALID_FACE_BOX, null, "[]", "[0.1,0.2,0.3,0.4]", "\"x\"", "5", "null", "true")
    }

    @Test
    fun `a box with a member missing is an invalid face box`() {
        for (member in listOf("left", "top", "right", "bottom")) {
            assertBoxValuesRejected(member, UnevaluatableReason.INVALID_FACE_BOX, null)
        }
    }

    @Test
    fun `a box member that is not a number is an invalid face box`() {
        for (member in listOf("left", "top", "right", "bottom")) {
            assertBoxValuesRejected(member, UnevaluatableReason.INVALID_FACE_BOX, "\"0.5\"", "null", "true", "[0.5]", "{}")
        }
    }

    @Test
    fun `a box member beyond float range is rejected`() {
        for (member in listOf("left", "top", "right", "bottom")) {
            assertBoxValuesRejected(member, UnevaluatableReason.INVALID_FACE_BOX, "1e400", "-1e400", "3.5e38")
        }
    }

    @Test
    fun `a box member outside zero to one is an invalid face box`() {
        assertBoxValuesRejected("left", UnevaluatableReason.INVALID_FACE_BOX, "-0.1", "-1")
        assertBoxValuesRejected("top", UnevaluatableReason.INVALID_FACE_BOX, "-0.1", "-1")
        assertBoxValuesRejected("right", UnevaluatableReason.INVALID_FACE_BOX, "1.1", "2")
        assertBoxValuesRejected("bottom", UnevaluatableReason.INVALID_FACE_BOX, "1.1", "2")
        // A member of the other end is outside too, even where order alone would pass.
        assertRejected(
            "left=1.1,right=1.2",
            boxDocument("left" to "1.1", "right" to "1.2"),
            UnevaluatableReason.INVALID_FACE_BOX
        )
    }

    @Test
    fun `a box with equal or inverted edges is an invalid face box`() {
        assertRejected("left==right", boxDocument("left" to "0.5"), UnevaluatableReason.INVALID_FACE_BOX)
        assertRejected("top==bottom", boxDocument("top" to "0.75"), UnevaluatableReason.INVALID_FACE_BOX)
        assertRejected("left>right", boxDocument("left" to "0.6"), UnevaluatableReason.INVALID_FACE_BOX)
        assertRejected("top>bottom", boxDocument("top" to "0.9"), UnevaluatableReason.INVALID_FACE_BOX)
        assertRejected("zero width", boxDocument("left" to "0", "right" to "0"), UnevaluatableReason.INVALID_FACE_BOX)
        assertRejected("one width", boxDocument("left" to "1", "right" to "1"), UnevaluatableReason.INVALID_FACE_BOX)
    }

    @Test
    fun `edges that differ in the text but are equal as floats are an invalid face box and never an unexpected error`() {
        assertRejected(
            "left vs right",
            boxDocument("left" to "0.5", "right" to "0.50000000001"),
            UnevaluatableReason.INVALID_FACE_BOX
        )
        assertRejected(
            "top vs bottom",
            boxDocument("top" to "0.25", "bottom" to "0.2500000001"),
            UnevaluatableReason.INVALID_FACE_BOX
        )
        assertRejected(
            "inverted beyond precision",
            boxDocument("left" to "0.5000000001", "right" to "0.5"),
            UnevaluatableReason.INVALID_FACE_BOX
        )
        assertRejected(
            "both underflow to zero",
            boxDocument("left" to "0", "right" to "1e-50"),
            UnevaluatableReason.INVALID_FACE_BOX
        )
    }

    @Test
    fun `a box member that rounds to one as a float is accepted though its text exceeds one`() {
        val clause = evaluatable(boxDocument("right" to "1.00000000001", "bottom" to "1")).clauses.single()

        clause as AlbumRuleClause.People
        assertEquals(1f, clause.box.right, 0f)
        assertEquals(1f, clause.box.bottom, 0f)
    }

    @Test
    fun `box members zero and one are numbers and are accepted`() {
        val clause = evaluatable(
            boxDocument("left" to "0", "top" to "0", "right" to "1", "bottom" to "1")
        ).clauses.single()

        assertEquals(person(box = FaceBox(0f, 0f, 1f, 1f)), clause)
    }

    @Test
    fun `an exponent form box member that is a valid number is read`() {
        val clause = evaluatable(boxDocument("left" to "2.5e-1", "right" to "5E-1")).clauses.single()

        assertEquals(person(), clause)
    }

    @Test
    fun `a negative zero left or top edge parses to positive zero and encodes as 0_0`() {
        for (token in listOf("-0", "-0.0", "-1e-50")) {
            for (field in listOf("left", "top")) {
                val clause = evaluatable(boxDocument(field to token)).clauses.single() as AlbumRuleClause.People

                val expected = if (field == "left") {
                    person(box = FaceBox(0f, 0.125f, 0.5f, 0.75f))
                } else {
                    person(box = FaceBox(0.25f, 0f, 0.5f, 0.75f))
                }
                assertEquals("$field=$token", expected, clause)
                assertSameBoxBits(expected.box, clause.box)
                assertTrue("$field=$token", AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(clause))).contains("\"$field\":0.0"))
            }
        }
    }

    @Test
    fun `a duplicate key inside box is a duplicate key`() {
        val duplicated = """{"left":0.1,"left":0.2,"top":0.125,"right":0.5,"bottom":0.75}"""

        assertRejected("duplicate box key", peopleDocument("box" to duplicated), UnevaluatableReason.DUPLICATE_KEY)
    }

    @Test
    fun `a duplicate key in a people clause is a duplicate key`() {
        val raw = envelope("""{"type":"people","pipeline":1,"pipeline":2,"uri":"$anchorUri",""" +
            """"displayName":"a","size":1,"dateModified":2,"box":{"left":0.1,"top":0.1,"right":0.2,"bottom":0.2}}""")

        assertUnevaluatable(raw, UnevaluatableReason.DUPLICATE_KEY)
    }

    @Test
    fun `a people document over the length cap is too long`() {
        val raw = peopleDocument("displayName" to "\"${"x".repeat(AlbumRuleJson.MAX_DOCUMENT_LENGTH)}\"")

        assertUnevaluatable(raw, UnevaluatableReason.TOO_LONG)
    }

    @Test
    fun `a box nested past the depth cap is too deep`() {
        val tooDeep = "[".repeat(AlbumRuleJson.MAX_DEPTH - 3) + """{"left":0.1}""" + "]".repeat(AlbumRuleJson.MAX_DEPTH - 3)

        assertRejected("deep box", peopleDocument("box" to tooDeep), UnevaluatableReason.TOO_DEEP)
    }

    @Test
    fun `a people clause under envelope version 2 is an unknown version`() {
        assertUnevaluatable(
            envelope(goldenPersonText, version = "2"),
            UnevaluatableReason.UNKNOWN_VERSION
        )
    }

    @Test
    fun `a people clause beside an unknown clause type makes the whole rule unevaluatable`() {
        assertUnevaluatable(
            envelope("""$goldenPersonText,{"type":"scene"}"""),
            UnevaluatableReason.UNKNOWN_CLAUSE_TYPE
        )
    }

    @Test
    fun `a people clause beside an invalid people clause makes the whole rule unevaluatable`() {
        val invalid = objectText(validClauseFields, arrayOf("pipeline" to "0"))

        assertUnevaluatable(envelope("$goldenPersonText,$invalid"), UnevaluatableReason.INVALID_PIPELINE_VERSION)
    }

    @Test
    fun `a people clause mixed with region and time in a handwritten document parses in order`() {
        val raw = envelope(
            """$timeClause,$goldenPersonText,{"type":"region","dataset":1,"country":"IT"}"""
        )

        assertEquals(
            listOf(time("2025-01-01", "2025-12-31"), person(), AlbumRuleClause.Region("IT")),
            evaluatable(raw).clauses
        )
    }

    @Test
    fun `a combined rule encodes the people clause in its place`() {
        val rule = AlbumRule.Evaluatable(
            listOf(time("2025-01-01", "2025-12-31"), person(), AlbumRuleClause.Region("IT"))
        )

        assertEquals(
            envelope(
                """$timeClause,$goldenPersonText,{"type":"region","dataset":1,"country":"IT"}"""
            ),
            AlbumRuleJson.encode(rule)
        )
    }

    @Test
    fun `every strict prefix of a valid people document is unevaluatable`() {
        val document = AlbumRuleJson.encode(
            AlbumRule.Evaluatable(listOf(time("2025-01-01", null), person(displayName = "Z\u00fcrich.jpg")))
        )

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
    fun `parse never throws or hits the safety net when a valid people document is corrupted one char at a time`() {
        val document = AlbumRuleJson.encode(
            AlbumRule.Evaluatable(listOf(person(displayName = "Z\u00fcrich.jpg", box = FaceBox(0.25f, 0.125f, 0.5f, 0.75f))))
        )
        val replacements = listOf('"', '\\', '{', '}', '[', ']', ',', ':', '0', '1', '9', '-', '\u0000', 'u', 'e', 'E', '.')
        val mutations = document.indices.flatMap { index ->
            listOf(document.removeRange(index, index + 1)) +
                replacements.map { document.replaceRange(index, index + 1, it.toString()) }
        }

        for (mutated in mutations) {
            val result = AlbumRuleJson.parse(mutated)
            assertNotNull(result)
            if (result is AlbumRule.Unevaluatable) {
                assertEquals(mutated, result.raw)
                assertNotEquals(mutated, UnevaluatableReason.UNEXPECTED_ERROR, result.reason)
            }
        }
    }

    // --- people: construction invariants ---

    @Test
    fun `a people clause needs a pipeline version of at least one`() {
        assertThrows(IllegalArgumentException::class.java) { person(pipelineVersion = 0) }
        assertThrows(IllegalArgumentException::class.java) { person(pipelineVersion = -1) }
        assertEquals(1, person(pipelineVersion = 1).pipelineVersion)
    }

    @Test
    fun `a people clause cannot have an empty uri or display name`() {
        assertThrows(IllegalArgumentException::class.java) { person(uri = "") }
        assertThrows(IllegalArgumentException::class.java) { person(displayName = "") }
    }

    @Test
    fun `no check message of a people clause names a value`() {
        val messages = listOf(
            assertThrows(IllegalArgumentException::class.java) { person(uri = secretUri, displayName = secretName, pipelineVersion = 0) },
            assertThrows(IllegalArgumentException::class.java) { person(uri = "", displayName = secretName, pipelineVersion = 5) },
            assertThrows(IllegalArgumentException::class.java) { person(uri = secretUri, displayName = "", pipelineVersion = 5) }
        ).map { it.message.orEmpty() }

        for (message in messages) {
            assertTrue(message, message.isNotEmpty())
            assertFalse(message, message.contains(secretUri) || message.contains(secretName) || message.contains("5"))
        }
    }

    @Test
    fun `copying a people clause into an invalid one is refused`() {
        assertThrows(IllegalArgumentException::class.java) { person().copy(pipelineVersion = 0) }
    }

    // --- people: anchoredAt ---

    @Test
    fun `anchoredAt copies the representative's identity box and pipeline version`() {
        val representative = FaceGroupRepresentative(
            identity = mediaIdentityOf("content://media/external/images/media/42", "face.jpg", 123L, 456L),
            box = FaceBox(0.1f, 0.2f, 0.3f, 0.4f),
            pipelineVersion = 7
        )

        val clause = AlbumRuleClause.People.anchoredAt(representative)

        assertEquals(representative.identity, clause.anchor)
        assertEquals(representative.box, clause.box)
        assertEquals(7, clause.pipelineVersion)
    }

    @Test
    fun `anchoredAt follows each representative and keeps nothing between calls`() {
        val first = FaceGroupRepresentative(
            mediaIdentityOf("content://media/external/images/media/1", "a.jpg", 1L, 2L),
            FaceBox(0.1f, 0.1f, 0.2f, 0.2f),
            1
        )
        val second = FaceGroupRepresentative(
            mediaIdentityOf("content://media/external/images/media/2", "b.jpg", 3L, 4L),
            FaceBox(0.5f, 0.5f, 0.9f, 0.9f),
            2
        )

        assertNotEquals(AlbumRuleClause.People.anchoredAt(first), AlbumRuleClause.People.anchoredAt(second))
        assertEquals(
            AlbumRuleClause.People(first.identity, first.box, first.pipelineVersion),
            AlbumRuleClause.People.anchoredAt(first)
        )
    }

    // --- people: nothing identifying is printed (ADR-0024 section 6) ---

    private val secretUri = "content://media/external/images/media/777001"
    private val secretName = "SecretHolidayPhoto.jpg"

    private fun secretPerson() = person(
        uri = secretUri,
        displayName = secretName,
        size = 31_415_926L,
        dateModified = 1_700_000_123L,
        box = FaceBox(0.3125f, 0.1875f, 0.6875f, 0.8125f),
        pipelineVersion = 4242
    )

    private fun assertPrintsNothingIdentifying(printed: String) {
        for (secret in listOf(secretUri, "777001", secretName, "31415926", "1700000123", "0.3125", "0.1875", "0.6875", "0.8125", "4242")) {
            assertFalse("$printed contains $secret", printed.contains(secret))
        }
    }

    @Test
    fun `a people clause prints nothing`() {
        val printed = secretPerson().toString()

        assertPrintsNothingIdentifying(printed)
        assertEquals("People(redacted)", printed)
    }

    @Test
    fun `an evaluatable rule holding a people clause prints nothing identifying`() {
        val rule = AlbumRule.Evaluatable(listOf(time("2025-01-01", null), secretPerson()))

        assertPrintsNothingIdentifying(rule.toString())
    }

    @Test
    fun `an unevaluatable people rule prints its reason and nothing of its text`() {
        val raw = AlbumRuleJson.encode(AlbumRule.Evaluatable(listOf(secretPerson()))).replace("\"pipeline\":4242", "\"pipeline\":0")
        val result = AlbumRuleJson.parse(raw)

        result as AlbumRule.Unevaluatable
        assertEquals(UnevaluatableReason.INVALID_PIPELINE_VERSION, result.reason)
        assertTrue(result.raw.contains(secretUri))
        assertPrintsNothingIdentifying(result.toString())
        assertTrue(result.toString(), result.toString().contains("INVALID_PIPELINE_VERSION"))
    }

    @Test
    fun `an unevaluatable rule of any kind prints its reason and not its raw text`() {
        val raw = envelope(timeClause, version = "2")
        val result = AlbumRuleJson.parse(raw)

        result as AlbumRule.Unevaluatable
        assertFalse(result.toString().contains("2025"))
        assertTrue(result.toString().contains("UNKNOWN_VERSION"))
    }
}

// Names a GeoNames row could plausibly carry, plus the ones that break a naive JSON writer.
private val HOSTILE_NAMES = listOf(
    "l'Aquila",
    "Z\u00fcrich",
    "Quote\"Town",
    "\"",
    "\\",
    "\\\\",
    "back\\slash\"quote",
    "Tab\there",
    "line\nbreak",
    "cr\rlf",
    "nul\u0000byte",
    "unit\u001fsep",
    "del\u007f",
    "next\u0085line",
    "\u2028\u2029",
    "\uFEFFbom",
    "\u05E9\u05DC\u05D5\u05DD",
    "\u0645\u0631\u062D\u0628\u0627",
    "abc\u202Edef",
    "\u200Fmark\u200E",
    "\uD83D\uDE00 emoji \uD83C\uDDEE\uD83C\uDDF9",
    "lone\uD800surrogate",
    "\uDC00low first",
    "\\u0041 is not an escape here",
    "x\",\"country\":\"FR",
    "\"}]}",
    "{\"type\":\"people\"}",
    "  padded  "
)

// The real gazetteer assets, read from the module directory like GazetteerAssetTest; a missing file fails.
class AlbumRuleRegionAssetNamesTest {

    private val assetDir = File("src/main/assets/gazetteer")

    private fun dataRows(asset: String, columns: Int): List<List<String>> {
        val file = File(assetDir, asset)
        assertTrue("missing ${file.absolutePath}", file.isFile)
        val rows = file.readLines(Charsets.UTF_8)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split('\t') }
        rows.forEach { assertEquals("$asset row $it", columns, it.size) }
        return rows
    }

    private fun assertAllRoundTrip(what: String, regions: List<AlbumRuleClause.Region>) {
        val failures = regions.filter { region ->
            val rule = AlbumRule.Evaluatable(listOf(region))
            AlbumRuleJson.parse(AlbumRuleJson.encode(rule)) != rule
        }

        assertTrue(
            "$what: ${failures.size} of ${regions.size} did not round trip; first: ${failures.take(5)}",
            failures.isEmpty()
        )
    }

    // places.tsv columns: latitude, longitude, name, country, admin1 code (may be empty).
    private fun placeRows(): List<List<String>> = dataRows("places.tsv", 5)

    // admin1.tsv columns: "CC.code", name.
    private fun admin1Rows(): List<List<String>> = dataRows("admin1.tsv", 2)

    @Test
    fun `every distinct place name in the real assets round trips as a place clause`() {
        val rows = placeRows().distinctBy { it[2] }
        assertTrue("only ${rows.size} distinct place names read", rows.size >= 30_000)

        assertAllRoundTrip("place names", rows.map { AlbumRuleClause.Region(country = it[3], place = it[2]) })
    }

    @Test
    fun `every admin1 name in the real assets round trips as a region clause`() {
        val rows = admin1Rows()
        assertTrue("only ${rows.size} admin1 rows read", rows.size >= 2_500)

        assertAllRoundTrip(
            "admin1 names",
            rows.map {
                AlbumRuleClause.Region(
                    country = it[0].substringBefore('.'),
                    admin1 = it[0].substringAfter('.'),
                    admin1Name = it[1]
                )
            }
        )
    }

    @Test
    fun `every real place with its real region round trips as a full place clause`() {
        val admin1Names = admin1Rows().associate { it[0] to it[1] }
        val regions = placeRows().mapNotNull { row ->
            val name = admin1Names["${row[3]}.${row[4]}"] ?: return@mapNotNull null
            AlbumRuleClause.Region(country = row[3], admin1 = row[4], admin1Name = name, place = row[2])
        }
        assertTrue("only ${regions.size} places with a known region", regions.size >= 30_000)

        assertAllRoundTrip("full place clauses", regions)
    }
}
