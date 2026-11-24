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
    data class Unevaluatable(val raw: String, val reason: UnevaluatableReason) : AlbumRule {
        // The text of an unreadable people rule names its anchor photo (ADR-0024 §6).
        override fun toString(): String = "Unevaluatable(raw=redacted, reason=$reason)"
    }
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

    /**
     * A place at country, region or place granularity (ADR-0017 Amendment 2). Codes are meaningful
     * only against [dataset], so a value from any dataset but the current one cannot be built: the
     * parser refuses such a document, and encoding it would write an album broken from creation.
     * [admin1] and [admin1Name] come as a pair; [admin1Name] is a guard and remap key, never matched.
     */
    data class Region(
        val country: String,
        val admin1: String? = null,
        val admin1Name: String? = null,
        val place: String? = null,
        val dataset: Int = PLACE_RESOLVER_VERSION
    ) : AlbumRuleClause {
        init {
            require(country.length == 2 && country.all { it in 'A'..'Z' }) {
                "country must be two upper-case ASCII letters"
            }
            require((admin1 == null) == (admin1Name == null)) { "admin1 and admin1Name come as a pair" }
            require(admin1 == null || admin1.isNotEmpty()) { "admin1 is empty" }
            require(admin1Name == null || admin1Name.isNotEmpty()) { "admin1Name is empty" }
            require(place == null || place.isNotEmpty()) { "place is empty" }
            require(dataset == PLACE_RESOLVER_VERSION) { "dataset must be the current resolver version" }
        }
    }

    /**
     * One person, named by a face in an anchor photo (ADR-0022 §1). [pipelineVersion] is informational: it is
     * the version the [box] was found under and is never compared. An anchor names a file and where a face is
     * in it, so nothing here prints and no check message names a value (ADR-0024 §6).
     */
    data class People(
        val anchor: MediaIdentity,
        val box: FaceBox,
        val pipelineVersion: Int
    ) : AlbumRuleClause {
        init {
            require(pipelineVersion >= 1) { "pipelineVersion must be at least 1" }
            require(anchor.mediaUri.isNotEmpty()) { "the anchor needs a URI" }
            require(anchor.healingKey.displayName.isNotEmpty()) { "the anchor needs a display name" }
        }

        override fun toString(): String = "People(redacted)"

        companion object {
            /** The face the picker showed, copied as it was read: identity, box and pipeline version only. */
            fun anchoredAt(representative: FaceGroupRepresentative): People {
                return People(
                    anchor = representative.identity,
                    box = representative.box,
                    pipelineVersion = representative.pipelineVersion
                )
            }
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
    INVALID_COUNTRY,
    INCOMPLETE_ADMIN1_PAIR,
    INVALID_REGION_NAME,
    INVALID_DATASET,

    /** A well-formed region clause stamped with a dataset other than the current one. */
    UNKNOWN_DATASET,
    INVALID_ANCHOR,
    INVALID_PIPELINE_VERSION,
    INVALID_FACE_BOX,
    UNEXPECTED_ERROR
}
