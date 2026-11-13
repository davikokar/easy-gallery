# ADR-0017: An automatic album's rule is a composable predicate

- Status: Active
- Date: 2026-10-08
- Decision makers: Easy Gallery project maintainer

## Context

[ADR-0012](0012-one-album-type-rule-additions-exclusions.md) settled that there is one album type
and that its members are `(rule matches ∪ additions) − exclusions`. It deliberately said nothing
about what a rule *is*. `AlbumEntity` carries a nullable `rule: String?` column, shipped in schema
version 1 and never yet written to; Stage 2 is the first stage that populates it.

Three forces decide its shape, and all three point past Stage 2.

**The epic needs compound rules eventually.** Stage 2 delivers region and time
([albums.md](../../design/albums.md) §4); Stage 3 adds people and Stage 4 adds content. "These two
people, in Italy, in 2025" is a rule that spans all three families. If each stage invents its own
storage, that rule is a fourth thing to invent, after three migrations.

**A rule is always read and written whole.** Resolution loads an album and evaluates its rule
against the index. Nothing asks "which albums have a region clause"; nothing updates one clause of
a rule in place. That is the access pattern of a document, not of a relation.

**Evaluation is not SQL.** Region and time clauses are a filter over indexed columns, but content
and people clauses are vector comparison across the library (albums.md §6). Any representation
that assumes the rule can be translated into a query is wrong by Stage 3.

A fourth force comes from backup. [ADR-0014](0014-album-database-included-in-auto-backup.md) puts
the album database into Android auto-backup, so a rule written by a newer version of the app can
arrive, intact, on an older one — and the same happens on a downgrade. Whatever the format is, an
old parser will one day meet a clause type it has never heard of, and what it does then is a
decision rather than an accident.

## Decision

**An automatic album's rule is a conjunction of typed clauses, serialised as one JSON document in
the existing `albums.rule` column.**

- The envelope is `{"version": 1, "all": [ <clause>, … ]}`. Each clause is an object carrying a
  `"type"` discriminator plus the fields that type defines. `"all"` names the conjunction
  explicitly, so adding `"any"` or `"none"` later is an addition to the format rather than a break
  in it.
- **No schema change.** `albums.rule` is already nullable `TEXT`. A rule is `NULL` for a manual
  album — ADR-0012's "empty rule" — and a document for an automatic one. An empty `"all"` array is
  never written; "no rule" is `NULL` and only `NULL`.
- **Stage 2 implements two clause types**, `time` and `region`. `people` and `content` are
  anticipated by the format and simply absent. Stage 3 therefore adds a clause type and a
  resolver, not a migration.

**The scope is deliberately narrow: conjunction only.** No disjunction, no negation, and no
nesting beyond this single level. "Italy or France" is not expressible, and the honest answer
today is two albums. Widening this is a new ADR, not an implementation detail, because every
consumer — the parser, the evaluator, the editing UI, and the unknown-clause rule below — gets
harder at the same moment.

**An unknown clause type makes an album unevaluatable. It does not make it empty, and it does not
make it everything.** Parsing yields either a fully understood rule or an `Unevaluatable` marker
that carries the raw document unchanged. An unevaluatable album:

- **resolves to its manual additions minus its exclusions.** The rule contributes nothing. Nothing
  is invented and nothing the user chose by hand is lost.
- **says so.** It is shown with a visible notice that it uses a feature from a newer version of
  Easy Gallery, in both Albums View and Album Detail View — the same two surfaces Stage 1's
  `album_hidden_items_notice` had to reach, for the same reason: an album that is quietly wrong is
  worse than one that explains itself (albums.md §6).
- **is never rewritten.** No save path may persist a rule it could not fully parse, and in
  particular may not "helpfully" drop the clause it did not understand. In a conjunction, removing
  a clause *widens* the result — an album that meant "in Italy, of these people" would silently
  become "of these people", permanently, on the next edit.
- **is never deleted**, by direct analogy with ADR-0010's rule that an unresolvable membership is
  retained rather than dropped.

The same treatment applies to an unknown envelope `version` and to malformed JSON. All three land
on `Unevaluatable`; none of them throws.

> **Amended 2026-10-26.** Amendment 1 below fixes the `time` clause's fields and the zone it is read
> in, and lists the further cases the parser sends to `Unevaluatable`. The text above is unchanged.
>
> **Amended 2026-11-11.** Amendment 2 below fixes the `region` clause's fields, the dataset stamp
> that keeps its codes meaningful, the obligation a later dataset change carries, and how a clause
> that cannot yet be settled is evaluated. The text above is unchanged.

## Alternatives Considered

**A column per clause family on `albums`** — `fromDate`, `toDate`, `countryCode`, `admin1Code`, and
so on. The most obvious answer and the most queryable. Rejected because it charges a migration to
every stage of the epic, and ADR-0009 made migrations a deliberate, permanent discipline rather
than a cheap operation. It also cannot express two clauses of the same family, and it produces a
wide, almost entirely null row for the manual albums that are the common case.

**A `rule_clauses` child table, one row per clause.** Genuinely credible: it is queryable in SQL,
it handles an unknown `type` string as ordinary data, and it fits the relational model the album
tables already use. Rejected because the query ability buys nothing — no code path wants to find
albums by clause — while the costs are real: a table, a foreign key, a clause ordering to define
and maintain, and a second write path for a value that is always read and written as a whole. A
value that is only ever handled whole belongs in one column.

**A full boolean expression tree, with AND, OR, NOT and nesting.** More expressive, and barely
harder to serialise. Rejected because the difficulty is not in the format. Evaluation needs
short-circuit semantics; the UI needs an expression editor, which is a substantial feature nobody
has asked for; and the unknown-clause rule above stops being stateable, because an unknown clause
under a negation widens or narrows depending on where it sits in the tree. Conjunction answers the
requirement that prompted this ADR.

**A query string — a SQL fragment, or a small expression DSL.** Rejected on three counts: it is an
injection surface written into a file that auto-backup carries between devices; it cannot describe
vector similarity, so Stages 3 and 4 would need a second mechanism anyway; and it cannot be
validated without being executed.

**Protobuf, or another binary encoding.** Smaller, and versioned by construction. Rejected because
it costs a dependency and a code-generation step to save a few hundred bytes per album, and because
it is unreadable in a bug report and in a database dump. JSON can be read by the person trying to
work out why a restored album is behaving oddly, which is precisely when it matters.

## Consequences

### Positive

- Stage 3 adds a clause type rather than a schema. The column, the parse contract, and the
  unknown-clause behaviour are settled once, before any of them has a second caller.
- Compound rules fall out for free. There is no "and also" feature to build later.
- **Stage 2's rules need no schema change at all**, because `albums.rule` has been nullable `TEXT`
  since version 1. The first index-related migration is therefore in the *other* database
  ([ADR-0018](0018-media-index-in-a-second-database.md)), not this one.
- An album written by a newer version degrades into something a user can read and recover from,
  instead of into an empty album that is indistinguishable from data loss.
- Parsing and evaluation are pure and Android-free, so they are unit-testable on a plain JVM
  alongside `AlbumMembership.kt` and `GalleryTransformations.kt`.

### Negative

- JSON in a column is opaque to SQL. "Which albums use a region clause?" becomes a Kotlin scan over
  every album. Accepted, because nothing needs to ask.
- Without a serialisation dependency the parser is hand-written, and a hand-written parser is
  exactly where the unknown-clause rule will be got wrong. Unknown clause type, unknown envelope
  version, and malformed JSON each need their own test, and all three must land on `Unevaluatable`
  rather than on an exception escaping into `viewModelScope`.
- Conjunction-only will be felt. Two albums is a worse answer than one album with an `OR`, and the
  pressure to widen the format will come from users rather than from developers.
- An unevaluatable album needs a user-facing string in all ten locales and a notice on two
  surfaces. That is real work created by a case that should never occur on a device that has only
  ever moved forward.
- The format cannot enforce that a clause and the index agree about time zones, units, or code
  vocabulary. A `region` clause storing an admin1 code means nothing unless the indexer writes the
  same vocabulary. That contract is held by tests and by ADR-0019, not by the schema.

## References

- [ADR-0012](0012-one-album-type-rule-additions-exclusions.md) — one album type; this ADR gives its
  "rule" a concrete form and must not contradict it
- [ADR-0009](0009-room-as-first-owned-datastore.md) — the datastore, and the migration discipline
  this decision is shaped to avoid paying per stage
- [ADR-0010](0010-album-membership-dual-key-identity.md) — the retain-rather-than-drop precedent the
  unknown-clause rule follows
- [ADR-0014](0014-album-database-included-in-auto-backup.md) — why a newer version's rule can arrive
  on an older app
- [ADR-0018](0018-media-index-in-a-second-database.md) — the index a rule is evaluated against
- [ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md) — the vocabulary a `region` clause
  is written in
- [docs/design/albums.md](../../design/albums.md) §3, §4, §6, §7;
  [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) step 2.5
- `app/src/main/java/com/davide/seddio/easygallery/data/AlbumEntities.kt` — the `rule` column this
  decision fills

## Amendment 1 — 2026-10-26 — The `time` clause, the zone it is read in, and how strict the parser is

This amendment **adds** to the Decision; it corrects nothing. The envelope, the conjunction-only
scope, the `NULL`-means-manual rule, and the whole of the unknown-clause rule continue to govern
exactly as written, and every alternative rejected above stays rejected.

It is recorded as an amendment rather than a superseding ADR, following ADR-0010 Amendment 1,
ADR-0011 Amendment 1, ADR-0014 Amendment 1 and ADR-0018 Amendments 1 and 2. The Decision named a
`time` clause and left its fields, its time base and the parser's edge cases to the step that
builds them. Step 2.5 of [albums-stage-2.md](../../design/albums-stage-2.md) is that step, and
these are the choices it made before writing code. They are recorded here because each one decides
which photos a user's album contains, and a choice of that kind made silently in a parser is the
kind that gets reversed by the next person who sees only its cost.

### The `time` clause

```json
{"type": "time", "from": "YYYY-MM-DD", "to": "YYYY-MM-DD"}
```

- **Both fields are ISO calendar dates with a four-digit year, and both are inclusive.** A rule
  for "2025" is `"from": "2025-01-01", "to": "2025-12-31"`; a single day has `from` equal to `to`.
- **Either may be omitted, but not both.** An omitted `from` is open towards the past and an
  omitted `to` is open towards the future. A clause with neither bound would match every dated
  item, which no picker will ever offer, so it is treated as malformed rather than as "everything".
- **Calendar dates, not instants, because that is what the user means.** The 2.7 date picker offers
  years and months, and "photos from March" is a statement about the calendar. Storing dates keeps
  the rule readable in a bug report and in a database dump — the reason this record chose JSON over
  a binary encoding.

### The zone the dates are read in

**The dates are read in the device's default zone at evaluation time** — the same rule step 2.3
applied to a zone-less EXIF `DateTimeOriginal`, which is interpreted in the device's zone at
extraction time and stated on `parseExifDateTakenEpochMillis`. Using the same rule on both sides is
the point: for the common case, a photo taken, indexed and browsed in one zone, the day the user
picks and the day the photo is filed under agree.

- **The zone is injected**, so tests can pin it, and production passes the system default read at
  the moment of evaluation — it is not cached across evaluations, so a device that changes zone
  evaluates in the new one next time.
- **The range in instants is half-open:** `[from.atStartOfDay(zone), (to + 1 day).atStartOfDay(zone))`.
  An omitted bound leaves that side open. **The end is not computed by adding 24 hours** to the
  start of `to`: a day on which daylight saving changes is 23 or 25 hours long, and the
  fixed-length version drops the last hour of one and swallows the first hour of the next.
- The index stores date-taken as epoch milliseconds UTC (step 2.3), so evaluation compares
  instants against instants; nothing is converted per row.

### An accepted limit

**The index stores only the instant, not the date the photo was taken on where it was taken.** A
photo whose EXIF carries `OffsetTimeOriginal` is stored correctly as an instant, but its
capture-local calendar date is not kept. Evaluated in another zone, a photo taken near midnight can
land on the neighbouring day — 23:30 on 31 December in New York is 05:30 on 1 January in Rome, so a
device in Rome files it under the next year. A zone-less photo has the same exposure in a different
form: its instant was fixed in whatever zone the device was in when it was indexed.

Fixing this properly needs a new index column for the capture-local date, which is a migration of
the index database and a full re-index. **That is out of scope for step 2.5 and is not scheduled.**
It is accepted because the error is bounded to items within a zone offset of midnight, and because
the alternatives below do not fix it either.

**A second accepted limit: an edited file keeps its previously indexed date until it is
re-indexed.** The evaluator matches a live item by URI first, and only the healing key would notice
an edit, so meanwhile the album can show complete with a stale date. Accepted because the index is
a recomputable cache that the next pass selecting the row corrects.

### How strict the parser is

The Decision already sends an unknown clause type, an unknown envelope version and malformed JSON to
`Unevaluatable`. Each of the following **also** yields `Unevaluatable`, never an exception and never
a guess:

- **an unknown field inside a known clause.** A newer version may add a field that narrows a clause;
  ignoring it widens the result, which is the same harm as dropping a clause, and the Decision
  already forbids that.
- **an empty `"all"` array.** The Decision says one is never written. Read as a vacuous conjunction
  it would match everything — the outcome the Decision rules out by name.
- **an inverted range** (`from` after `to`), and **an invalid date** — one that does not exist on
  the calendar, or is not in the `YYYY-MM-DD` form with a four-digit year.
- **a `version` that is not the integer `1`.** A string `"1"` is not `1`.
- **duplicate keys**, at any level. Which one wins is parser-dependent, so the document has no single
  meaning.
- **a root that is not a JSON object**, and **trailing content** after the root object.
- **nesting depth or document length over the parser's caps.** The caps are constants in the parser,
  which owns their values; they bound the cost of reading a hostile or corrupt document carried in
  by backup.
- **a non-`NULL` value that is empty, whitespace, or the text `null`.** Only SQL `NULL` means
  manual. A column holding *something* was written by somebody, and treating it as manual would let
  the next save write `NULL` over it — the silent rewrite the Decision forbids.

**A `region` clause is an unknown clause type until step 2.7** implements it. That is the Decision's
rule applied, not an exception to it: a region rule could only reach a pre-2.7 build from a newer
one, and it must land on `Unevaluatable` rather than be ignored.

### Items with no date

**An item the index has not reached, and an indexed item with no EXIF date, never match a `time`
clause. There is no fallback to `dateAdded`.** These are the `NotIndexed` and `IndexedWithoutDate`
states step 2.4 named in `DateTakenSortState`.
An encoder's "no date" value — a video container's zero creation date, read back as 1904-01-01, or any other such sentinel — counts as no date, not as a date ([ADR-0018](0018-media-index-in-a-second-database.md) Amendment 3).

Sorting degrades those two states to `dateAdded` because a sort must put every item somewhere. A
filter does not, and `dateAdded` is when the file reached the device — a 2019 photo received in a
chat in 2025 would join a "2025" album it has nothing to do with. An album that invents a member is
worse than one that is short. The cost is that an automatic album over a partly indexed library is
short until indexing catches up; making that visible rather than silent belongs to step 2.5's exit
criteria and is not decided here.

### Alternatives considered

**A zone stored in the rule.** Each `time` clause would carry the zone it was created in, and
evaluation would use that rather than the device's. Rejected because it gives the rule a second
source of zone truth that disagrees with the first: zone-less photos were already pinned to the
device's zone at extraction, so a rule read in a different stored zone shifts their boundaries
rather than fixing them. It also does nothing for the accepted limit above, which needs the
capture-local date and not a better zone for the range; and it leaves a user who has moved
looking at an album that disagrees, by a few hours at each end, with every other date the app
shows them.

**Absolute instants stored in the rule.** `from` and `to` as epoch milliseconds, computed once when
the rule is written. Zone-free at evaluation and trivially exact. Rejected because it freezes the
creation-time zone into the rule invisibly — the same frozen zone as the alternative above, minus
the field that would at least say so. It throws away the user's intent: "March 2025" becomes two
numbers, which a bug report and a database dump cannot read, and which an editing UI must convert
back into dates through a zone that may since have changed. And it moves the daylight-saving
arithmetic onto the write path, where getting it wrong is permanent rather than corrected on the
next evaluation.

### When this format becomes binding

**The format becomes stored user data only when step 2.7 first writes a rule.** Step 2.5 ships the
parser, the evaluator and a test-only way to create a rule; no shipped code path writes `albums.rule`
before 2.7, so no device holds a rule in this format. Until then every choice in this amendment can
change freely — amend this record, with no version bump and no compatibility path. From the first
rule a user saves, the Decision's unknown-clause rule and the envelope `version` govern every change,
and this amendment stops being cheap to revise.

### Review

**The decision-maker's review of the zone choice is welcome**, and the window above is the time for
it. It is the one choice here a reasonable person could make the other way: it is right for a user
who stays in one zone, and approximate for one who travels.

### References

- [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) steps 2.3, 2.4, 2.5 and 2.7
- `parseExifDateTakenEpochMillis` — the zone-less EXIF rule this amendment mirrors
- `DateTakenSortState` — the three date-taken states, and the sort-side `dateAdded` fallback a
  `time` clause deliberately does not share
- [ADR-0018](0018-media-index-in-a-second-database.md) — the index a capture-local date column would
  have to be added to

## Amendment 2 — 2026-11-11 — The `region` clause, its dataset stamp, and unsettled items

This amendment **adds** to the Decision and to Amendment 1; it corrects nothing. The envelope, the
conjunction-only scope, the `NULL`-means-manual rule, the unknown-clause rule, and every strictness
rule of Amendment 1 continue to govern a `region` clause exactly as they govern a `time` clause.

It is an amendment rather than a new ADR for Amendment 1's reason: the Decision named a `region`
clause and left its fields to the step that builds it. Step 2.7 of
[albums-stage-2.md](../../design/albums-stage-2.md) is that step. **The maintainer approved what
follows on 2026-11-11, before any of it was coded.** Each choice decides which photos a user's album
holds, and two of them bind builds that do not exist yet, which is why they are written down rather
than left in a parser.

### The `region` clause

One clause type, three granularities:

```json
{"type": "region", "dataset": 1, "country": "IT"}
{"type": "region", "dataset": 1, "country": "IT", "admin1": "16", "admin1Name": "Tuscany"}
{"type": "region", "dataset": 1, "country": "IT", "admin1": "16", "admin1Name": "Tuscany", "place": "Florence"}
{"type": "region", "dataset": 1, "country": "SG", "place": "Singapore"}
```

- **`country`** is the ISO 3166-1 alpha-2 code, in upper case, as the index's `derivedCountryCode`
  holds it.
- **`admin1` and `admin1Name` are the region, and come as a pair or not at all.** `admin1` is the
  GeoNames admin1 code exactly as `derivedRegionCode` holds it — bare, without the country prefix
  `admin1.tsv` keys it by. `admin1Name` is that region's name in GeoNames' `name` form, as
  `admin1.tsv` carries it ([ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md)
  Amendment 1 §4).
- **`place` is optional**, with or without the region pair, and is the `derivedPlaceName` the
  resolver wrote.
- **A place without the region pair matches only rows whose region is null.** A place in a country
  with no admin1 division, or whose admin1 code is `00`, resolves to a null region (ADR-0019
  Amendment 1 §7) — Singapore is the example — and a clause naming it omits the pair. Omitting the
  pair never means "in any region": that would merge same-named places in different regions, which
  is what the pair is there to keep apart.
- **`dataset` is the `PLACE_RESOLVER_VERSION` in force when the clause was written**
  ([ADR-0018](0018-media-index-in-a-second-database.md) Amendment 4).

### Matching

- **While `dataset` equals the current `PLACE_RESOLVER_VERSION`, matching is by code**, exactly:
  `country` against `derivedCountryCode`, `admin1` against `derivedRegionCode`, `place` against
  `derivedPlaceName`. Every field present must match, and a place clause without the pair also
  requires a null region.
- **Names are guards and remap keys; step 2.7 does not match on them.** `admin1Name` is compared
  with nothing at evaluation. It is stored because a code means nothing without the dataset that
  defined it, and a name is what a later remap — below — finds a region by. `place` is the one name
  that is also matched, because the index holds no code for a place.
- **Same-named places within one country and region merge.** Two places called the same within one
  region are one clause target. Accepted because the picker builds its list from the same derived
  columns and so already shows them as one entry: the album and the picker agree about what was
  chosen.
- **A `dataset` other than the current version makes the rule `Unevaluatable`, with the new reason
  `UNKNOWN_DATASET`.** A higher number is a rule from a newer build, arriving by restore or
  downgrade; a lower one is a rule from before a bump. Either way its codes were defined by a dataset
  this build does not hold, and matching them against an index resolved by another is a silent wrong
  answer — the outcome the Decision's unknown-clause rule exists to prevent. The album resolves to
  its additions minus its exclusions, shows the Newer-app notice, and is never rewritten.

### The stability plan, and the obligation it puts on a later build

GeoNames admin1 codes can change between dataset versions — most are FIPS codes, "subject to change
to iso code" in GeoNames' own readme ([ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md),
*Consequences*). The index survives that by re-resolving every row after a bump; a code stored in a
user's rule has no such repair. The `dataset` stamp makes the mismatch detectable from the rule
alone. What makes it survivable is this:

**Binding: the first build that bumps `PLACE_RESOLVER_VERSION` must ship a remap for clauses
carrying an older `dataset`.**

- The remap finds the region by `admin1Name` within the clause's `country` in the new dataset, then
  the place by name within that region — or among the country's null-region places, for a clause
  without the pair.
- **Where no unique answer exists, the clause degrades to a state that build's ADR defines**, chosen
  there with the new dataset's actual differences in view rather than guessed here.
- **The stored rule is never rewritten.** The remap applies when the rule is read, as the Decision
  requires of any document the build did not fully write; the stored text stays as it was, so a
  downgrade still reads the original.
- **That build also meets pending rows still carrying old-dataset values** — Tier A and a heal keep
  derived values until the next resolve (ADR-0018 Amendment 4) — and decides what a provisional
  match against them means. 2.7 holds one dataset, so it cannot meet this case and does not decide
  it.

Without the remap, one bump would turn every region album a user owns into a permanent Newer-app
notice whose wording is false — the app is not older; the rule is. **The obligation is what keeps
`UNKNOWN_DATASET` a restore-or-downgrade case only**, and the Newer-app wording true.

**A suggested test:** a unit test that pins `PLACE_RESOLVER_VERSION == 1` and whose failure message
names this amendment, so a bump cannot land without its author meeting the obligation. The change
that ships the remap deletes it.

### GeoNames IDs were not adopted

Storing a place's or region's stable `geonameid` instead of a code would survive a dataset change
with no remap, and would keep same-named places apart. Rejected for its cost: the shipped assets
carry no IDs (ADR-0019 Amendment 1 §2), so adopting them means regenerating the assets, bumping the
resolver version and re-resolving every located row, a new derived column and therefore index
version 5, and more APK size — all paid now, for a dataset change that may never come. The stamp
and the remap defer the cost to the build that causes it. **What is given up** is the separation of
same-named places, accepted above.

### How strict the parser is about a `region` clause

Each of the following yields `Unevaluatable` — never an exception and never a guess — in addition to
Amendment 1's list, which applies to this clause unchanged:

- **an unknown field** — anything but `type`, `dataset`, `country`, `admin1`, `admin1Name` and
  `place`. Amendment 1's reason holds: a field a newer build added may narrow the clause.
- **`admin1` without `admin1Name`, or `admin1Name` without `admin1`.**
- **a missing `country`, or one that is not exactly two upper-case ASCII letters.** `"it"` is not
  `"IT"`; normalising it would be the parser guessing.
- **an `admin1`, `admin1Name` or `place` that is not a string, or is empty.**
- **a missing `dataset`, or one that is not a JSON integer.** `"1"` is not `1`, as Amendment 1 says
  of the envelope `version`, and `1.0` is not `1` either. A well-formed integer that is not the
  current version is `UNKNOWN_DATASET`, not malformed.

The reason values are `UnevaluatableReason`'s, in `data/AlbumRule.kt`, which owns their names;
`UNKNOWN_DATASET` is the one this amendment adds by name, because it is the one case where the
document is well formed and merely from another dataset.

**The encoder escapes JSON strings.** `admin1Name` and `place` are free text from GeoNames, with
apostrophes, diacritics, and whatever else the dataset holds. A name that broke the encoder would
write a document the parser sends to `Unevaluatable` — an album broken from the moment it is
created, and never rewritten. So every name in the shipped assets must round-trip through encode
and parse unchanged, which is an exit criterion of step 2.7.

### Combined rules

**A `time` clause and a `region` clause combine by AND in one rule, and step 2.7 ships it.** That is
the Decision's conjunction applied, not a new rule. 2.7 writes at most one clause of each type. The
parser and the evaluator accept any number and AND them all: two `region` clauses naming different
countries is a valid rule that matches nothing, not a malformed one.

### Settled and unsettled

Amendment 1's rule that an item the index has not reached never matches is unchanged. With places,
an item can also be indexed and still waiting for the resolver, so for a live item each clause gives
one of three answers:

| | `time` | `region` |
|---|---|---|
| **Match** | indexed with a date in range | resolved — marker current — and its codes match |
| **Settled no-match** | indexed with a date outside the range, or with no date | no coordinates; resolved beyond the cap; resolved and its codes differ |
| **Unsettled** | not indexed | not indexed; pending — marker null or not current |

- **A pending row that still carries old derived values matches on them provisionally, and is
  unsettled.** ADR-0018 Amendment 4 keeps those values so labels do not flicker, and evaluation uses
  them for the same reason: an item should not leave the album at every re-extraction and return at
  the next pass. It stays unsettled because its values may be wrong — the row is pending for a
  reason. A pending row with no derived values does not match, and is unsettled.
- **Out-of-cap and no-coordinate rows are settled no-matches.** Waiting changes neither; counting
  them as waiting is the permanent-waiting failure ADR-0018 Amendment 4 names.

For an item that is **neither added nor excluded**:

- it is **a member** when every clause matches, provisionally or settled;
- it **makes the album show the Incomplete-album notice** when some clause is unsettled and none is
  a settled no-match.

A settled no-match on any clause takes the item out of the question. **An item dated outside the
range whose location is pending does not show the notice**, because no resolution of its place could
make it match — and that is what keeps a combined album from waiting on every pending row in the
library. For a time-only album these rules reduce exactly to step 2.5's. Additions and exclusions
are untouched: ADR-0012's formula applies after the clauses have answered.

> **Amended 2026-11-13 — see Amendment 3 below.** The album no longer shows the Incomplete-album
> notice. The settled/unsettled model above, and the waiting status the evaluator reports, are
> unchanged.

### When this becomes binding

As Amendment 1 says of the `time` clause: the `region` format becomes stored user data when a
shipped build first writes one, which is step 2.7. Until then it can change by amending this record.
After it, the Decision's unknown-clause rule, the `dataset` stamp and the remap obligation govern
every change.

### Consequences of this amendment

**Positive**

- A dataset change is detectable from the rule alone, and survivable without rewriting stored data.
- 2.7 needs no asset regeneration, no resolver bump, and no index migration.
- An album reports itself incomplete only while waiting could change its answer, so combined albums
  stay honest without waiting forever.

**Negative**

- **A `PLACE_RESOLVER_VERSION` bump is no longer one constant**, as ADR-0018 Amendment 4's
  *Re-resolution is one constant* put it: it now costs a remap and an ADR choosing the degraded state. Deliberate — the cost lands
  on the change that causes it — but that build will be larger than it looks.
- The remap matches by name, so a region renamed between datasets defeats it and lands on the
  degraded state.
- Same-named places within one country and region merge.
- A provisional match can show an item whose new place differs, until the next resolve pass; the
  Incomplete-album notice is shown meanwhile.

  > **Amended 2026-11-13 — see Amendment 3 below.** No notice is shown meanwhile any more.

### References

- [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) step 2.7
- [docs/design/albums.md](../../design/albums.md) §8 decisions 25 and 26
- [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 4 — the marker, pending rows, and
  why their old values are kept
- [ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md) Amendment 1 — the assets, the
  `name` form, the `00` admin1 code, and the cap
- `data/AlbumRule.kt`, `data/AlbumRuleJson.kt` — the clause types, the reasons, the parser and the
  encoder; `PLACE_RESOLVER_VERSION` in `data/Gazetteer.kt`

## Amendment 3 — 2026-11-13 — A waiting album no longer shows a notice

This amendment changes what the user sees, not what the evaluator computes. The Decision, Amendment
1, and Amendment 2's clauses, matching rules and settled/unsettled model all continue to govern
unchanged. **The maintainer decided it on 2026-11-13**; [albums.md](../../design/albums.md) §8
decision 29 records the decision, its reasons, and its accepted cost.

### What changed

- **The album no longer shows the Incomplete-album notice** — not on the Album tile, not on the
  Album row, not in Album Detail View. Amendment 2's bullet "it makes the album show the
  Incomplete-album notice" now reads "it makes the album waiting": the condition is the same, and
  nothing displays it.
- **The evaluator's output is unchanged.** An album with an unsettled clause and no settled
  no-match on it is still reported as waiting on the index — `AlbumEvaluationStatus.WAITING_ON_INDEX`.
  Only its surfacing is removed.
- **The Newer-app notice is unaffected.** An unevaluatable album still says so, as the Decision
  requires.

### Why the status is kept

The settled/unsettled model is what stops an album waiting forever on rows that will never change,
and it is correct whether or not anything shows it. Keeping the status leaves this a decision about
the UI alone: removing it would change the evaluator for a reason that is not about evaluation, and
showing the album's waiting state again later needs no change here.

### Consequences of this amendment

**Negative**

- An album waiting on the index can look short or empty with nothing on it saying why. Settings'
  Media index area and the New automatic album screen's Index band still say how much is not yet
  indexed.
- Amendment 2's Positive — "an album reports itself incomplete only while waiting could change its
  answer" — now holds of the status the evaluator reports, not of anything the user sees.
- A provisional match whose place may still change is shown with no sign that it is provisional.

### References added by this amendment

- [docs/design/albums.md](../../design/albums.md) §8 decisions 22 and 29
