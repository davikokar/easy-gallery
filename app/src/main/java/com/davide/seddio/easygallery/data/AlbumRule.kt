package com.davide.seddio.easygallery.data

import java.time.LocalDate

/**
 * What a non-NULL `albums.rule` value means (ADR-0017). A NULL column is a manual album and has no
 * [AlbumRule] value at all; [AlbumRuleJson.parse] returns null for it.
 */
sealed interface AlbumRule {

    /** A rule this build fully understands. Never empty: "no rule" is NULL and only NULL. */
    data class Evaluatable(val clauses: List<AlbumRuleClause>) : AlbumRule {
        init {
            require(clauses.isNotEmpty()) { "A rule needs at least one clause" }
        }
    }

    /**
     * A stored rule this build cannot evaluate. [raw] is the stored text exactly as read and must
     * never be rewritten, trimmed or re-encoded (ADR-0017). There is deliberately no way to encode
     * one: [AlbumRuleJson.encode] takes [Evaluatable] only.
     */
    data class Unevaluatable(val raw: String, val reason: UnevaluatableReason) : AlbumRule
}

sealed interface AlbumRuleClause {

    /** Inclusive calendar dates, read in the device zone at evaluation time (ADR-0017 Amendment 1). */
    data class Time(val from: LocalDate?, val to: LocalDate?) : AlbumRuleClause {
        init {
            require(from != null || to != null) { "A time clause needs at least one bound" }
            require(from == null || to == null || !from.isAfter(to)) { "from is after to" }
            require(from == null || from.year in SUPPORTED_YEARS) { "from year out of range" }
            require(to == null || to.year in SUPPORTED_YEARS) { "to year out of range" }
        }
    }
}

// The stored form is a four-digit year, so only these years can be written and read back.
private val SUPPORTED_YEARS = 0..9999

enum class UnevaluatableReason {
    BLANK_OR_NULL_TEXT,
    TOO_LONG,
    TOO_DEEP,
    MALFORMED_JSON,
    DUPLICATE_KEY,
    TRAILING_CONTENT,
    NOT_AN_OBJECT,
    UNKNOWN_VERSION,
    UNKNOWN_ENVELOPE_KEY,
    INVALID_ENVELOPE,
    EMPTY_CONJUNCTION,
    INVALID_CLAUSE,
    UNKNOWN_CLAUSE_TYPE,
    UNKNOWN_CLAUSE_FIELD,
    INVALID_DATE,
    INVERTED_RANGE,
    NO_TIME_BOUND,
    UNEXPECTED_ERROR
}
