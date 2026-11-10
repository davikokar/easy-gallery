# ADR-0018: The media index is a second database

- Status: Active
- Date: 2026-10-08
- Decision makers: Easy Gallery project maintainer

## Context

Stage 2 builds the media index ([albums.md](../../design/albums.md) §6): per-item facts computed in
the background for every image and video on the device. Tier A is one row per item — on a large
library an estimated 50,000+, a sizing figure inherited from
[ADR-0009](0009-room-as-first-owned-datastore.md) and
[ADR-0014](0014-album-database-included-in-auto-backup.md) rather than a count anyone has taken.
Tier B adds a content embedding per image and Tier C adds face detections and embeddings.

> **Qualified in place on 2026-10-08, the day this record was adopted.** The row count above was
> first written as a fact. It is an estimate, and what leans on it is worth stating: none of the
> decision does. The exclusion rests on the index being recomputable and on backup rules selecting
> files rather than tables, and both hold at any size — the figure only describes how much of the
> quota would be at risk if the index were included. The real count becomes measurable once the
> index exists; record it in repo memory then.

[ADR-0009](0009-room-as-first-owned-datastore.md) chose Room explicitly on the index's
requirements rather than on Stage 1's, so *what* stores the index is settled. *Where* it lives is
not, and Stage 1 left exactly one database — `EasyGalleryDatabase`, holding albums and their
memberships.

The app's backup posture, verified against the repository on 2026-10-08:

- `AndroidManifest.xml` sets `android:allowBackup="true"` and points at both rules files.
- `res/xml/backup_rules.xml` is a `<full-backup-content>` element with every sample `<include>` and
  `<exclude>` still commented out.
- `res/xml/data_extraction_rules.xml` has a `<cloud-backup>` section whose contents are a commented
  TODO, and a `<device-transfer>` section that is commented out in its entirety.

Auto Backup includes the directory returned by `getDatabasePath()` by default, so **anything Room
creates is in scope for backup the day it is created**, with no action taken.

[ADR-0014](0014-album-database-included-in-auto-backup.md) adopted that for the album database
deliberately, and flagged two loose ends it explicitly deferred to this stage:

- albums.md §8.9 commits that face data "never leaves the device, never enters a backup, never
  appears in a log". ADR-0014 noted that honouring this "will require an explicit `<exclude>` when
  Tier C lands".
- The 25 MB per-app backup quota is shared. Exceeding it calls `onQuotaExceeded()` and stops the
  cloud backup **entirely** — it does not drop the largest file and keep the rest. ADR-0014 said
  Stage 2 "must decide explicitly whether the index is excluded… rather than discovered when a
  user's albums stop backing up".

This ADR is that decision, taken before any index code is written rather than after.

## Decision

**The media index lives in its own Room database, separate from `EasyGalleryDatabase`, and is
excluded from backup.**

- A second `RoomDatabase` class, with its own file name, its own version number, and its own
  exported schema. `room.schemaLocation` in `app/build.gradle.kts` already points at
  `app/schemas`, and Room writes one subdirectory per database class, so the second schema
  directory appears without any build configuration — and, like the first, must be committed.
- **ADR-0009's singleton rule now applies twice.** `EasyGalleryApp` holds both instances, each
  `by lazy`, and neither may be constructed anywhere else. Room's invalidation tracking depends on
  there being one instance per database file; that is now two invariants to keep rather than one,
  and the second is easier to forget precisely because the first one looked like a one-off.
- Access follows the convention the preference stores and `AlbumStore` already established: an
  interface, a Room-backed production implementation, an `InMemory*` implementation for unit tests,
  and DAO-level tests on `inMemoryDatabaseBuilder`.
- **The backup exclusion is part of this decision, not a follow-up.** The index database file is
  excluded in `backup_rules.xml` and in **both** the `<cloud-backup>` and `<device-transfer>`
  sections of `data_extraction_rules.xml`. Adding an `<exclude>` does not change what is included
  by default; only the first `<include>` does that, and ADR-0014's warning against adding one
  stands unchanged.

**The index stores captured facts and derived facts in separate columns.**

- **Captured**: the raw EXIF facts read from the file — latitude, longitude, true date-taken,
  pixel dimensions. Expensive: one file open and parse per image, which is precisely the cost
  [ADR-0001](0001-on-demand-media-location-metadata.md) refused to pay during a query. Captured
  once, in the background.
- **Derived**: country, region and place codes computed from that stored latitude and longitude.
  Cheap, and recomputable with no file I/O at all.

### Why a second database rather than more tables in the first

The decisive argument is the collision ADR-0014 left open, and it has no solution inside one file:

- **Android backup rules select files, not tables.** The moment Tier C face data is a table in
  `easy_gallery.db`, ADR-0014's decision to back that file up and §8.9's commitment that face data
  never enters a backup are in direct contradiction, and there is no `<exclude>` that can resolve
  it. One database file cannot be half backed up. A second file can be excluded outright.
- **The index is entirely recomputable from the device's own media.** Backing it up spends the
  shared 25 MB quota on data that will be rebuilt anyway, and risks `onQuotaExceeded()` taking down
  the backup of albums — the one thing in this app that genuinely cannot be recomputed.
- **A restored index would be wrong in the way ADR-0014 describes for memberships.** Every
  `MediaStore` key is reassigned by the receiving device. For a membership that is worth healing,
  because it represents work the user did. For a derived cache it is worth discarding.

### Why captured and derived are split

Splitting them is what makes [ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md)
reversible, and it is the reason the place codes are columns on the index rather than values folded
into the capture step.

Replacing or improving the gazetteer then means re-running a resolve pass over the index's own
latitude and longitude columns — the cheap half only, with no file access — rather than re-indexing
the library. The expensive thing is captured once and kept. The cheap, fallible thing stays
disposable.

## Alternatives Considered

**Index tables inside `EasyGalleryDatabase`.** One database, one singleton, one migration history,
and the ability to join an album to the rows its rule matches. Rejected on the backup collision
above, which has no answer inside a single file, and on the quota risk to albums. The join is not
the loss it looks like: under [ADR-0017](0017-composable-album-rule-representation.md) a rule is a
document evaluated in Kotlin, not a query, and Stages 3 and 4 evaluate by vector comparison, which
is not a join in any case.

**One database, excluded from backup as a whole.** Resolves the conflict by reversing ADR-0014, so
albums stop being backed up. Rejected outright. ADR-0014 sets out at length why losing a user's
albums with their device is the one outcome worth accepting real risk to avoid, and nothing in
Stage 2 weakens that argument.

**A custom `BackupAgent` that backs up some tables and not others.** Rejected for the reason
ADR-0014 already gives against agents generally: during backup and restore the app runs in a
restricted mode in which content providers are not initialised, and a hand-written agent replaces a
system guarantee with app code on the one path that executes when the user is already in trouble.

**Keep the index out of Room entirely — flat files, or `getCacheDir()`.** The cache directory is
excluded from backup for free, which is genuinely attractive here. Rejected because the index is
the exact workload ADR-0009 chose Room *for*: one row per media item, queried by range over
latitude, longitude and capture date — a shape that argues for a database whatever the library
turns out to hold. And the cache directory is the directory the system is
entitled to delete under storage pressure, which would silently discard minutes of background work
and leave every automatic album quietly incomplete.

## Consequences

### Positive

- §8.9's commitment is honoured structurally, before Tier C exists, rather than depending on
  someone remembering an `<exclude>` in Stage 3.
- The album backup stays well inside its quota, and the index's eventual size — unknown today, and
  plausibly large once Tiers B and C land — can never threaten it.
- The two databases version independently. An index schema change touches neither the album schema
  nor its migrations, and vice versa.
- Swapping the gazetteer costs a resolve pass, not a re-index.
- "Delete the index" becomes a supported operation on a whole file, which is what §8.9's required
  Settings switch — the one that *deletes* face data rather than merely stopping its collection —
  will need in Stage 3.

### Negative

- Two singletons on `EasyGalleryApp`, two schema directories, two migration histories, two sets of
  DAO tests, and two chances to construct a second instance by accident. ADR-0009 warned that the
  first singleton must not turn `EasyGalleryApp` into a service locator; this is the second entry
  on that list and the warning is now load-bearing.
- No foreign keys and no joins between an album and an index row. Any relationship between the two
  is maintained in Kotlin, and nothing in the database will notice it going wrong.
- Two databases mean two transaction scopes, so an operation spanning both cannot be atomic.
  Nothing in Stage 2 needs one. If something later does, that is a reason to revisit this ADR
  rather than to reach for a workaround.
- **The index is lost on device replacement** and must be rebuilt from nothing on the new device —
  minutes of background work during which region and time albums are incomplete, on a device where
  the user's albums have just been restored and look right. §6's rule that staleness must be
  visible is what keeps this honest rather than broken, and it stops being a refinement here: it is
  the only thing standing between a correct restore and a user who thinks their albums lost their
  photos.
- The exclusion has to be written correctly in three places, and a mistake is invisible — the app
  behaves identically whether or not the index is being backed up, so only a review catches it.
  Whether Room's `-wal` and `-shm` sidecar files need `<exclude>` entries of their own is **not**
  settled by this ADR and must be checked when the rules are written.

## References

- [ADR-0009](0009-room-as-first-owned-datastore.md) — Room, chosen on this index's requirements;
  the singleton rule that now applies twice
- [ADR-0014](0014-album-database-included-in-auto-backup.md) — the backup posture this extends, and
  the two loose ends it deferred to Stage 2
- [ADR-0001](0001-on-demand-media-location-metadata.md) — extended, not superseded: lazy per-item
  reads stay correct for the info surfaces; the index is a second, amortised path
- [ADR-0017](0017-composable-album-rule-representation.md) — what is evaluated against this index
- [ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md) — what fills the derived columns,
  and why they are separate from the captured ones
- [docs/design/albums.md](../../design/albums.md) §6, §8.9;
  [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) steps 2.1–2.3
- `app/src/main/java/com/davide/seddio/easygallery/EasyGalleryApp.kt`,
  `data/EasyGalleryDatabase.kt`, `app/build.gradle.kts` (`room.schemaLocation`), `app/schemas/`
- `app/src/main/AndroidManifest.xml`, `res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml` — the unauthored state this decision is the first to change
- <https://developer.android.com/identity/data/autobackup> — files included by default, the 25 MB
  quota, and `onQuotaExceeded()`

## Amendment 1 — 2026-10-09 — The index also holds the delta pipeline's scheduling state

This amendment **adds** to the Decision; it corrects nothing. Everything in the record continues to
govern — the second database, the exclusion from backup in all three places, the captured/derived
column split, the singleton rule applying twice, and every alternative rejected in *Alternatives
Considered*.

It is recorded as an amendment rather than a superseding ADR, following ADR-0010 Amendment 1,
ADR-0011 Amendment 1 and ADR-0016 Amendment 1. The reasoning that chose a separate, backup-excluded
database did not change, and the new content is **an instance of that reasoning rather than an
exception to it**. Disabling this record and restating it would retire a decision that still
governs.

### What changed

The Decision described the index as one row per media item. Step 2.2 of
[albums-stage-2.md](../../design/albums-stage-2.md) added a second table holding **scheduling
state**: the per-volume delta watermark — the last-seen `MediaStore` version string, the last-seen
generation number, and the `DATE_MODIFIED` fallback watermark used on API 28–29 — keyed by volume
name.

So the index database now holds two kinds of thing: facts *about* media, and the bookkeeping that
decides which media to look at next. Both are covered by the same rules this record already states.

### Why it belongs here rather than in `SharedPreferences`

It was first implemented in the `app_settings` preferences file, beside the album watermark, and
moved. The watermark is **a map keyed by volume name, not a scalar**, and `AGENTS.md` and
`ARCHITECTURE.md` §6 both say that file may take another scalar and nothing structured. That ruled
out the cheap option and left two candidates — a sixth preferences file, or the index database.

The index database won on three of this record's own arguments:

- **It is recomputable.** A lost watermark costs one full resync, which is exactly the cost this
  record already accepts for the rows themselves. It is not a record of user intent, and ADR-0020's
  distinction between a user's choice and a cache puts it firmly on the cache side.
- **It must not be backed up**, for the same reason the rows must not: every `MediaStore` key and
  every generation number is meaningless on the receiving device, and a restored watermark would be
  actively worse than an absent one, because it would suppress the resync that rebuilds the index.
  Putting it in the excluded file gets that for free; a preferences file would have needed a fourth
  `<exclude>` in three places, with the same invisible-failure mode this record's *Negative*
  consequences already describe.
- **It is worthless without the index.** "Delete the index" must leave no stale watermark behind,
  and this record already makes that a supported whole-file operation. Two stores in two mechanisms
  would make it two operations that must not drift apart.

### Consequences

- **The index database is at version 2.** The watermark table arrived through a real
  `Migration(1, 2)` registered on the builder, with **no destructive fallback**, and Room exports
  one JSON per version under `app/schemas/` — `1.json` and `2.json`. **Each must be committed by
  the change that produces it.** Whether either is tracked at any given moment is a fact
  `git ls-files app/schemas` owns, and no document should restate it; the directory is outside
  `app/src/`, so a `git add` aimed there misses it silently. This is the first migration either
  database has had, and it is the precedent for the rest of Stage 2.
- The *Positive* consequence that the two databases version independently is now load-bearing
  rather than theoretical: this bump touched neither the album schema nor its history.
- Watermark access follows the same store convention as everything else here —
  `MediaIndexWatermarkStore`, with `Room*` and `InMemory*` implementations. That is a third store
  interface over two databases, not a new pattern.
- The *Negative* consequence about two transaction scopes is unaffected. The watermark and the rows
  it describes are in the **same** database, so the one place atomicity would have mattered is the
  one place it is available.

## Amendment 2 — 2026-10-12 — The index's run state is a preferences file, and why Amendment 1's test does not reach it

This amendment **adds** to the record; it corrects nothing, and in particular it does not weaken
Amendment 1. The watermark stays in the index database for every reason Amendment 1 gives.

It exists because Amendment 1 reads like a general rule and is not one. Step 2.4 of
[albums-stage-2.md](../../design/albums-stage-2.md) added a **sixth `SharedPreferences` file**,
`media_index_run_state`, holding the last indexing run's outcome, its start and finish timestamps,
and its row counters. That file passes Amendment 1's three-part test — it is disposable, it is
worthless without the index, and it must not be backed up — and it was still kept in
`SharedPreferences`. Without this record the next person meets a decision that looks like its own
precedent being ignored, and re-derives it wrongly in whichever direction they happen to start
from.

### Decision

**`media_index_run_state` stays in `SharedPreferences`.** It is not moved into the index database.

### Why the three-part test does not decide it

Amendment 1's three arguments were not a general test for "may this live in Room". They were the
tiebreak between a sixth preferences file and the index database, **after** a prior test had
already ruled out `app_settings`: the watermark is a map keyed by volume name, and `app_settings`
is allowed another scalar and nothing structured. That prior test is the one that decides here, and
it points the other way. Run state is twelve fixed, flat scalars — three `Long?` timestamps, six
`Int` counters, one enum name, and nothing whose key depends on the data. There is no structure for
a table to express that a preferences file cannot.

The three arguments then fail to separate the two candidates, because each of them is about
*correctness coupling*, and run state has none:

- **"Recomputable" is the weakest of the three, and it is the one that actually differs.** A lost
  watermark is recomputed by a full resync, which produces a correct index. A lost run state is not
  recomputed at all — nothing can reconstruct when the last run finished. It is simply disposable,
  which is a different property and a weaker claim on durable storage, not a stronger one.
- **"Worthless without the index" is true of both, but the stakes are not the same.** A watermark
  that outlives its index is *actively harmful*: it suppresses the resync that would rebuild the
  index, and the user gets a silently incomplete index with no error. A run state that outlives its
  index is *cosmetically wrong*: a Settings label says a scan happened, and the figures beside it —
  which are intersected with live media and read from the index itself, not from this file —
  already say the index is empty. The worst case is a stale timestamp until the next run. Losing it
  costs a blank label; losing the watermark costs a wrong index.
- **"Must not be backed up" is satisfied either way**, and the cost of satisfying it outside the
  database has now been paid. Amendment 1 counted a fourth `<exclude>` in three places against the
  preferences option. Those entries exist, were written correctly, and have been read back;
  [ADR-0014](0014-album-database-included-in-auto-backup.md) Amendment 1 records the posture they
  fit into. An argument from a cost that is already paid is not an argument.

### What it is, which is the thing that settles it

The watermark is part of the index. The index cannot be correctly extended without it, and "delete
the index" must take it with it — which is why Amendment 1 insists on one whole-file operation.
Run state is a report *about a job*, not part of the data the job produces. Deleting the index
without deleting the run state leaves a stale label, not a broken index, so the two do not have to
move together and nothing is bought by making them.

### Consequences

- **The boundary now has a worked example on each side**, which is more useful than either alone:
  structured, correctness-bearing index bookkeeping goes in the index database; flat, disposable
  reporting about a run stays in `SharedPreferences`. `AGENTS.md`'s *Preference stores* section and
  `ARCHITECTURE.md` §6 carry both.
- **"Delete the index" is now two operations, deliberately.** Dropping the database file leaves the
  run state behind. That is acceptable — the first subsequent run overwrites it, and in the interim
  the Settings figures that come from the index read zero while the timestamp reads stale. §8.9's
  future Settings switch should clear it for tidiness, not for correctness.
- **This record does not license a seventh preferences file.** The same two tests apply next time,
  in the same order: structured goes in a database; flat and disposable may stay. Anything that the
  index's correctness depends on belongs with the index whatever its shape.
- `media_index_run_state` deliberately holds **no** liveness flag. A persisted `is_running` was
  implemented and removed: it survives process death, so a killed worker left the Index now action
  disabled permanently. Liveness is derived from WorkManager's `WorkInfo` instead. That is not a
  storage decision and needs no ADR, but it is why the store writes fewer keys than its state class
  has fields.

## Amendment 3 — 2026-10-30 — A stored encoder sentinel is cleared by a migration, and the index reaches version 3

This amendment **adds** to the record; it corrects nothing in the Decision or in Amendments 1 and 2.
It is an amendment rather than a new ADR for the reason Amendment 1 gives: the index is still the
separate, backup-excluded, recomputable database this record chose, and what follows applies
Amendment 1's precedent — bump, migrate, commit the JSON — rather than departing from it. Amendment
1's statement that the index is at version 2 was true when written and is left as written.

### What happened

A video container writes a creation time of 0 when it has no date.
`MediaMetadataRetriever.METADATA_KEY_DATE` renders that as `19040101T000000.000Z`, the container
epoch — established from AOSP source (android13-release, `MPEG4Extractor::convertTimeToDate`), not
yet confirmed on hardware. Step 2.3's video date parser accepted it as a real capture date and stored
epoch milliseconds `-2082844800000`. On the one device measured, a Samsung SM-G781B, most videos
carried it, and `MediaStore`'s `DATE_TAKEN` was null for every one of them. Until it was fixed such a
video counted as *indexed with a date* in Settings, sorted into 1904 under date-taken sort, and would
have matched any `time` rule spanning 1904.

The parser now treats exactly that instant, and its textual renderings, as absent:
`parseVideoDateTakenEpochMillis` in `data/MediaIndexTierAIndexer.kt` returns null for it, while its
neighbours — one second later, one day later — and genuinely old dates such as 1950 stay valid.
EXIF's all-zero date forms were already rejected. It is the trap Null Island is the classic case of:
an encoder's "no value" stored as a fact.
[ADR-0017](0017-composable-album-rule-representation.md) Amendment 1 now says that such a sentinel
counts as no date.

### Decision

**Rows already stored with the sentinel are corrected by a real migration, `MIGRATION_2_3`, which
takes the index database to version 3.** It sets the captured date to null where it equals exactly
the sentinel, reading the parser's shared `QUICKTIME_ZERO_EPOCH_MILLIS` constant rather than a second
copy of the value. It re-extracts nothing, forces no resync, and is registered beside
`MIGRATION_1_2` with no destructive fallback. The entity did not change, so the schema's identity
hash did not either; Room still exports a `3.json` for the new version, and it must be committed by
the change that produces it, as Amendment 1 requires of every version.

### Why a migration

- **Fixing the parser alone repairs nothing already stored.** Delta detection selects only media
  that changed since the watermark, and an unchanged video is never re-extracted, so on an existing
  install the 1904 value would stay for the life of the row.
- **Normalising at read time was rejected.** It would put the sentinel in two places — the parser
  and every reader — and leave the stored data wrong for the next reader that does not know to
  filter it.
- **A forced resync was rejected.** Clearing the watermarks would re-open every indexed file to
  repair one value that needs no file at all, which is the most expensive repair available; and a
  resync is scoped per volume by design ([albums.md](../../design/albums.md) §6,
  [ADR-0020](0020-automatic-albums-resolve-against-live-media.md) clause 4), so a whole-index one is
  not a tool this design offers.

### Consequences

- **A migration can correct data, not only schema.** The test this case passes: the correct value is
  computable from the stored one, without the file. A future extraction fix that fails that test —
  the right value needs the file re-read — cannot be repaired by a migration, and is a choice
  between re-indexing and living with it.
- **One real date is given up.** A video genuinely created at exactly 1904-01-01T00:00:00Z is now
  undated. Accepted: no such video is plausible, and a sentinel matched by a plausibility window
  instead would have discarded real old dates.
- The album database is untouched; the two still version independently.

### References

- `data/MediaIndexTierAIndexer.kt` — `parseVideoDateTakenEpochMillis` and `QUICKTIME_ZERO_EPOCH_MILLIS`
- `data/MediaIndexDatabase.kt` — `MIGRATION_2_3`
- `MediaIndexTierAExtractionTest` and `MediaIndexDatabaseMigrationTest`, under `app/src/test/`
- `app/schemas/com.davide.seddio.easygallery.data.MediaIndexDatabase/3.json`
- [ADR-0017](0017-composable-album-rule-representation.md) Amendment 1, *Items with no date*
- [docs/design/albums.md](../../design/albums.md) §8 decision 21 — what an undated item gets instead
  of a second date source

## Amendment 4 — 2026-11-09 — A resolver-version marker, and the index reaches version 4

This amendment **adds** to the record; it corrects nothing in the Decision or in Amendments 1–3. It
is an amendment rather than a new ADR for the reason Amendment 1 gives: the index is still the
separate, backup-excluded, recomputable database this record chose, and what follows applies
Amendment 1's precedent — bump, migrate, commit the JSON. Amendment 3's statement that the index is
at version 3 was true when written and is left as written.

It is written **before** the code, as step 2.6 of [albums-stage-2.md](../../design/albums-stage-2.md)
requires of a decision; the version the index has actually reached is its `@Database` annotation's.

### The problem

The Decision split captured from derived columns so that the gazetteer could be swapped by a resolve
pass. It did not say how a row records that it **has** been resolved, and the three derived columns
cannot say it alone: a row resolved beyond
[ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md)'s distance cap has null country,
region and place — exactly what a row nobody has resolved yet has. Step 2.7 needs three states for a
row that has coordinates:

1. **resolved, with a place;**
2. **resolved, without one** — beyond the cap;
3. **not yet resolved.**

Without a marker, states 2 and 3 are the same row, and both consumers go wrong:

- **The resolve pass** can only select "derived columns are null", so every out-of-cap row is
  selected and recomputed on every run, forever.
- **The evaluator** cannot tell waiting from out-of-cap. Read null as waiting, and one mid-ocean
  photo keeps a region album waiting permanently; read null as resolved, and an album reports
  complete while rows are still unresolved. Those are the **permanent-waiting and false-complete**
  failures the review of step 2.5 named, now with a second cause.

### Decision

- **A nullable `INTEGER` column, `derivedPlaceResolverVersion`, on the index row.** Null means no
  resolver has written the row's derived columns. A value is the `PLACE_RESOLVER_VERSION` that
  wrote them. A row with coordinates is **pending** when the marker is null or differs from the
  current constant, and **resolved** otherwise — with a place or without one.
- **The index goes to version 4 through `MIGRATION_3_4`**, an `ALTER TABLE … ADD COLUMN` with no
  default, so every existing row reads null — pending, which is true: nothing has ever resolved it.
  It is registered beside `MIGRATION_1_2` and `MIGRATION_2_3` with no destructive fallback. This is a
  schema change, so the identity hash changes, and `4.json` is exported and must be committed by the
  change that produces it.
- **No database index on the column.** The pass reads the located rows in any case, and adding one
  later costs a version bump — the same cost as now, without a measured reason to pay it.
- **A row without coordinates is never stamped.** "No location" is read from the captured columns,
  not from the marker, and no resolver has anything to say about it.

### Who writes the marker

- **Tier A writes it null on every row it re-extracts, and keeps the row's old derived values.** A
  re-extracted file may have new coordinates, so the old answer may be wrong — but it is still the
  best label until the resolver catches up, and blanking it would make labels flicker on every edit.
  Null says "check this again". It also makes Tier A's read-then-replace safe against a resolver
  write that lands between the read and the write: the row ends pending and is resolved again.
- **A reconciler heal keeps the marker.** A heal rewrites the URI and the healing triple, never the
  coordinates, so the answer is still right. A heal copies the row, so this holds as long as the
  store's row model carries the marker — and the `InMemory*` store must carry it too, or the unit
  tests stop describing production.
- **The resolver writes the derived columns and the marker together, conditionally**: the update
  matches on `mediaUri` **and** the exact `capturedLatitude` and `capturedLongitude` the answer was
  computed from. If the row was re-extracted, healed or deleted in the meantime, the update matches
  nothing and the row is left as it is — pending, if Tier A touched it — for the next run. The
  periodic job and Index now are separate unique works and **can run at the same time**; an
  unconditional write could stamp an answer computed from coordinates the row no longer holds,
  marked current and therefore never revisited. Comparing `REAL`s for equality is sound here, because
  the values compared are the ones read from the same column.

### Re-resolution is one constant

`PLACE_RESOLVER_VERSION` is bumped when the dataset is regenerated, the cap changes, or the
algorithm changes. Every row stamped with an older value becomes pending, and the next pass
resolves it again with **no file I/O** — the captured/derived split doing what it was chosen for.
A bump needs **no migration**. The asset header carries the same number and a unit test fails unless
the two are equal; [ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md) Amendment 1 §7
holds that rule. One number serves all three causes because to a stored row they mean the same
thing: computed by a resolver that is no longer the current one.

### Alternatives considered

- **Recompute every located row on every run**, with no column and no migration. Rejected. It does
  not remove the ambiguity — a row Tier A writes after a pass reads exactly like an out-of-cap row
  until the next one, so the evaluator still cannot tell waiting from out-of-cap. And it rewrites
  every located row on every run: each write invalidates the index table, so every rule album
  re-evaluates after every batch — the cost the review of 2.5 already flagged — and the index
  churns for answers that did not change.
- **A sentinel in the derived columns** — an empty-string country for "beyond the cap". Rejected.
  It stores "no value" as a value, the trap Null Island and the 1904 container date both are, and
  it cannot say which resolver produced a row, so a dataset change could not mark rows stale.
- **A boolean "resolved" flag.** Rejected: it cannot express "resolved by an older resolver", so a
  dataset change would need a data migration to clear every flag instead of a constant bump.

### Consequences

- The three states are derivable from the row alone, by the resolve pass and the evaluator alike.
- **A pending row can carry derived values** — the last answer, kept so labels do not flicker. That
  is a label, not a resolution: whether step 2.7's evaluator counts a pending located row as waiting
  is 2.7's to decide, and the marker is what lets it decide.
- **A photo with GPS gets its place only after the next worker run**, because the resolver runs in
  the worker. Same shape as [albums.md](../../design/albums.md) §10 question 7; recorded as a risk
  for 2.7 rather than solved here.
- Every writer of index rows must carry the marker, and a writer that builds a row without it
  silently resets it. That is one more field for the next writer to know about; the Tier A rule
  above is the one place a reset is deliberate.
- The album database is untouched; the two still version independently.

### References

- `data/MediaIndexEntities.kt` — the derived columns; `data/MediaIndexDatabase.kt` — the migrations
- `data/MediaIndexTierAIndexer.kt` — re-extraction already carries the existing derived values
- `data/MediaIndexReconciliation.kt` — a heal copies the row
- `data/MediaIndexingWork.kt` — the two unique work names that let runs overlap
- [ADR-0019](0019-offline-reverse-geocoding-for-region-albums.md) Amendment 1 — the cap, the
  dataset stamp, and the rule that an unusable asset stamps nothing
- [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) step 2.6, and the review notes
  under 2.5 that name the permanent-waiting and false-complete failures
