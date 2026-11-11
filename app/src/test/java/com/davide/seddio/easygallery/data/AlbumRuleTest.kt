package com.davide.seddio.easygallery.data

import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
