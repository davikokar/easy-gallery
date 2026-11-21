# ADR-0018: The media index is a second database

- Status: Active
- Date: 2026-10-08
- Decision makers: Easy Gallery project maintainer

> ~~**Amendment 8 (2026-11-20), at the end of this record, is adopted and not yet built.** The `Active`
> status above covers the Decision and Amendments 1–8. Remove this note when step 3.4 has built it.~~
> **Amendment 8 was reversed on 2026-11-20, the day it was adopted, before anything of it was built**
> — see its *Reversed* note at the end of this record. The `Active` status above covers the Decision
> and Amendments 1–7; Amendment 8 is kept as history only. The index stays at version 6.

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

## Amendment 5 — 2026-11-18 — Tier C: face data in the index, and the index reaches version 5 (adopted 2026-11-19)

> **Adopted 2026-11-19, when the maintainer approved it.** Drafted by the Planner as step 3.1 of
> [albums-stage-3.md](../../design/albums-stage-3.md), and labelled *proposed* in this record until
> then, because the instructions' status vocabulary has no state for a draft; this record's own
> status was `Active` throughout, for the Decision and Amendments 1–4, and now covers this
> amendment too. Nothing below was built before adoption; step 3.2 builds it.
>
> **Its question was answered by the maintainer on 2026-11-19, as recommended**
> ([albums.md](../../design/albums.md) §8 decision 47). ~~That settles the question; it does not
> approve the amendment, which stays proposed until he does.~~ He approved it separately, the same
> day.

This amendment **adds** to the record; it corrects nothing in the Decision or in Amendments 1–4. It
is an amendment rather than a new ADR for Amendment 1's reason: the Decision put Tier C in this
database from the start, for the backup collision it names, and what follows applies Amendment 1's
precedent — bump, migrate, commit the JSON. The figures and choices it relies on are
[albums.md](../../design/albums.md) §8 decisions 9, 40, 45 and 46, and the face pipeline is
[ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md).

### What Tier C stores

Three kinds of row, in tables of their own. Their names and columns are step 3.2's; what each holds
is decided here.

1. **A face-indexing state per image** — one row per image the Tier C pass has processed, keyed by
   the image's index-row key. It holds the **face-pipeline version** that processed it and the
   **outcome**: faces stored (possibly none at or above the cut-off), or **failed**.
2. **A face** — one row per face at or above the 40 px cut-off (decision 46): its image's key, its
   **box** in the upright frame as fractions of the photo's width and height (the same frame as an
   anchor, [ADR-0022](0022-people-criterion-anchored-to-a-photo.md)), its **embedding** as the
   recogniser outputs it, and its **group**, null until grouping has run.
3. **Grouping bookkeeping** — enough to tell whether every stored face has been grouped under the
   current grouping parameters (the threshold of decision 45), and to keep a group's id stable for
   the picker's last tie-break (decision 46) within one grouping. Whether per-group summaries (image
   count, most recent photo, medoid face) are stored or computed on read is step 3.4's.

**Nothing else.** No crop, no thumbnail and no person name is stored anywhere. Faces under the cut-off
are not stored at all: they are never grouped, offered or matched (decision 46), so keeping them would
be face data with no use.

> **Step 3.2, 2026-11-19:** two tables, `media_index_face_state` and `media_index_face`. The third
> kind of row, grouping bookkeeping, is a grouping version on each face rather than a table of its
> own; per-group summaries stay step 3.4's. The schema and its reasons are in
> [albums-stage-3.md](../../design/albums-stage-3.md), *3.2 — Tier C schema and store*.

> ~~**Amendment 8, 2026-11-20:** a face row also holds its box's short side in pixels of the decoded
> frame, 0 meaning unknown, so that a group's representative can prefer a face at least 60 px across.
> "Nothing else" above still holds: it is a fact about a stored face, not a crop.~~ **Reversed the same
> day, before it was built** (Amendment 8's *Reversed* note): no pixel size is stored; the list above
> is again the whole of a face row.

> **Built 2026-11-21, by step 3.4** ([albums-stage-3.md](../../design/albums-stage-3.md), *3.4 —
> Built and verified*). Grouping fills the face row's two grouping columns and adds nothing else:
> `groupId` is the face id of the group's representative — the plain medoid, ties to the lower face
> id — and `groupingVersion` is `FACE_GROUPING_VERSION`, 1, naming the algorithm, the cut and the
> representative rule. Per-group summaries are **computed on read** by the People picker's query,
> not stored, so there is no third kind of row. **No schema change:** the index stays at version 6;
> Amendment 8, which would have added a column, was reversed before it was built and stays below as
> annotated history.

### Two stamps, by the captured/derived split

The Decision split captured EXIF facts from the place codes derived from them, so that the cheap half
could be recomputed without the files. Tier C has the same two halves, and versions them separately:

- **Captured: detection and embedding**, stamped with the **face-pipeline version**. It covers the
  detector and recogniser weights, the decode and detection sizes, the detector's score threshold and
  the 40 px cut-off ([ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) owns the constant).
  A bump makes every image pending again — a file open and inference per image, the expensive kind of
  re-index.
- **Derived: grouping**, stamped with a **grouping version** covering the threshold and the
  algorithm. A bump regroups the stored embeddings with **no file I/O and no inference**.

### Per-image states, so that "waiting on the index" is decidable

For a live **image** (decision 40: videos are never face-indexed; ~~GIFs — see the question below~~
nor are GIFs — the question below, answered 2026-11-19):

| State | Means | For a `people` clause |
|---|---|---|
| **Pending** | no state row; or its pipeline version is not the current one; or a face of it is not yet grouped under the current grouping version | unsettled — waiting on the index |
| **Done, faces** | current state row, its faces grouped | match or settled no-match, by group |
| **Done, none** | current state row, no face at or above the cut-off | settled no-match |
| **Failed** | inference declined or failed on this image | settled no-match: **neither pending nor covered**, §6's third state |

- **Failed is stored as absent, never as a guess**, as §6 rules for every tier. It is not retried
  until the pipeline version changes.
- **What counts as failed.** A failure a retry could cure — the worker stopped, cancelled, or out of
  memory — writes nothing, so the image stays pending. But an image that fails that way on every
  attempt would keep albums waiting for ever, the permanent-waiting failure Amendment 4 names; so
  after a bounded number of attempts it is recorded as failed. The bound and how attempts are counted
  are step 3.3's.
- **A video or GIF has no state row and is never pending.** Counting it as pending would keep every
  people album waiting for ever.

> **Built 2026-11-21, by step 3.4, for the grouping half of *Pending*.** Grouping is needed when a
> face at the current pipeline version has no grouping version or another one, or a `groupId` naming
> no face at that version — a representative deleted, or dropped by a changed healing triple; a
> deleted face that was not a representative does not make grouping needed. Faces at another
> pipeline version are ignored. How a `people` clause reads these states per image is step 3.5's.

> **Built 2026-11-21, by step 3.5** ([albums-stage-3.md](../../design/albums-stage-3.md), *3.5 —
> Built and verified 2026-11-21*). The per-image read is `observeFaceEvaluationProjection`: one Room
> query, the face state inner-joined to `media_index` and left-joined to its faces at the asked
> pipeline version, with **no embedding**; it re-emits on face writes and on group-only writes. **No
> schema change:** the index stays at version 6.

### Keeping Tier C true to the image it describes

These are Amendment 4's rules for its marker, applied to Tier C:

- ~~**Tier A re-extracting an image** — the file changed — **makes its Tier C state pending.** The
  faces of a changed file may have changed too.~~

  > **Reversed 2026-11-20 — see Amendment 6 below.** A re-extraction is not evidence that the file
  > changed. Tier C is now dropped only when the row's healing triple changed; an unchanged triple
  > keeps it. The bullet is struck rather than deleted because its reason — a changed file's faces
  > may have changed — still stands; what was wrong is the test for a changed file.
- **A reconciler heal rewrites the key** on the image's Tier C rows, in the same transaction as the
  image row, and keeps them: a heal is a move, not a change of content. *Amended 2026-11-20
  (Amendment 6): kept only while the healed row's healing triple equals the moved row's, which every
  heal the reconciler makes satisfies.*
- **Reconciliation deleting an image row deletes its Tier C rows** with it, in the same transaction.
  They are a cache ([ADR-0020](0020-automatic-albums-resolve-against-live-media.md)), and an
  unmounted volume keeps them, by ADR-0020 clause 4.
- **Writes are conditional.** The periodic run and Index now can overlap (Amendment 4). A Tier C
  result is written only if the image row is still the one it was computed from, and grouping must
  not run twice at once. The mechanism is steps 3.3 and 3.4's; the requirement is fixed here.

  > **Built 2026-11-21, by step 3.4, for grouping.** Grouping runs in the worker after the face
  > pass, inside the same hold of the Tier C lock, so it never runs twice at once. It writes every
  > group in one transaction, only to faces still at the pipeline version it read, and reads the
  > face-data setting inside that transaction (Amendment 7 §4); a face added since the read keeps a
  > null grouping version, so the next run groups again.

### The migration

**`Migration(4, 5)` creates the Tier C tables, empty, and changes no existing table.** Every image
therefore starts pending, which is true. It is registered beside the three earlier migrations with no
destructive fallback, the index reaches version 5, and `5.json` is exported and must be committed by
the change that produces it. The version actually reached is the `@Database` annotation's.

### Backup

~~**No rule changes.**~~ **One rule added at step 3.2 — the note below.** The Tier C tables are in `media_index.db`, which `backup_rules.xml` and both
sections of `data_extraction_rules.xml` already exclude, with its `-wal` and `-shm` files. The stage's
exit criteria check the rules files rather than assume them, and if step 3.2's deletion (below)
introduces any other sidecar file, that file needs the same three exclusions.

> **Step 3.2, 2026-11-19 — `media_index.db-journal` excluded as well.** The deletion added no file,
> but the re-check found one already possible: Room's automatic journal mode is a rollback journal
> on a low-RAM device, which leaves `media_index.db-journal`, and one left hot by a crash holds
> pages — face data, now — as they were before the transaction. It gained the same exclusion in all
> three places, `<exclude>` only. Recorded in [albums-stage-3.md](../../design/albums-stage-3.md),
> *3.2 — Tier C schema and store*.

### Deleting face data

[ADR-0024](0024-face-data-disclosure-and-delete-switch.md) decides when face data is deleted. What a
deletion removes, and how, is decided here:

- **Every Tier C row** — faces, per-image states and grouping bookkeeping — in **one transaction**.
  Tier A rows, the place columns and the delta watermarks are untouched.
- **Not by deleting the database file.** The Decision's *Positive* consequences, and
  [albums.md](../../design/albums.md) §8 decision 20, foresaw the switch deleting the whole file.
  That would also throw away Tier A, and re-reading every file's EXIF to rebuild it buys nothing for
  privacy. The forecast is not followed, deliberately.
- **No deleted face data may survive in the file.** SQLite leaves deleted rows' bytes in free pages
  and in the write-ahead log until they are reused or checkpointed. Step 3.2 chooses the mechanism —
  for example a checkpoint and `VACUUM` after the delete, or `secure_delete` on the connection — and a
  test proves the result.
- **Idempotent.** Deleting when nothing is stored does nothing; a deletion interrupted by process death
  is a rolled-back transaction, and ADR-0024 repeats it at the next start.
- **The run-state file holds no Tier C figure.** Any Tier C figure Settings shows is computed from the
  Tier C tables, so a deletion clears it with nothing else to touch. That keeps Amendment 2's file free
  of anything face-derived; Amendment 2's note that the switch "should clear it for tidiness" no
  longer applies.

> **Step 3.2, 2026-11-19 — the mechanism chosen: `secure_delete`, then a `TRUNCATE` checkpoint.**
> `PRAGMA secure_delete = 1` is set whenever the index opens, so every Tier C delete or update zeroes
> the old bytes, and again as the deletion transaction's first statement, where it must read back as
> on or nothing is deleted. After the commit, `PRAGMA wal_checkpoint(TRUNCATE)` empties the
> write-ahead log; a reader still holding the old snapshot leaves it incomplete, the deletion reports
> that, and the caller repeats it. `VACUUM` was rejected: it rewrites all of Tier A on every
> switch-off, builds its copy in RAM on Android, and one cut short is recovered only by another full
> vacuum. `MediaIndexFaceDataScrubTest` proves the result under Robolectric; what only a device can
> show is the stage plan's step 3.7. Recorded in
> [albums-stage-3.md](../../design/albums-stage-3.md), *3.2 — Tier C schema and store*.

> **Amended 2026-11-20 — see Amendment 6 below.** The note above covers the write-ahead log only.
> With a rollback journal the checkpoint is a no-op that reads as success, so the result said
> scrubbed whatever the journal mode; a mode that keeps page copies after commit now reports not
> scrubbed.

### Alternatives considered

- **Tier C columns on the existing image row**, as Amendment 4 did for its marker. Cheaper by one
  table. Rejected: a deletion would then be an update across every Tier A row, a face count would sit
  beside EXIF facts that are not face data, and every Tier A writer would have one more set of fields
  to carry or silently reset — the negative Amendment 4 already records for one field.
- **Deleting the whole index file** for the switch. Rejected above.
- **Storing faces under the cut-off** for a later change of cut-off. Rejected: that change is a
  pipeline-version bump, which re-detects anyway.
- **Storing the face crop** for the picker. Rejected: a crop is face data, and the picker can crop
  the photo at display time from the stored box.

### Consequences

**Positive**

- "Waiting on the index" is decidable per image, and a people album cannot wait on an item that will
  never change.
- A threshold change regroups without touching a file.
- Deleting face data is a contained operation that leaves Tier A alone.

**Negative**

- ~~Three more tables~~ Two more tables (step 3.2, 2026-11-19), one more migration, two more stamps to carry, and every Tier A writer must now
  also make Tier C pending ~~on re-extraction~~ when a re-extraction changes the healing triple
  (Amendment 6, 2026-11-20).
- A pipeline-version bump re-detects every image — of the order of minutes per thousand photos on the
  one phone measured, in the foreground ([albums-stage-3.md](../../design/albums-stage-3.md),
  *Spike outcome*).
- Embeddings make the index file larger; it stays out of backup, so only the device pays.

### Question for the maintainer — answered 2026-11-19

> **Answered by the maintainer on 2026-11-19**, in his words "go with the recommendations". The
> question is left as it was put, because its reasons are the record of why.

1. **GIFs.** The spike excluded GIFs and the stage plan lists them as out of scope "by decision", but
   no recorded decision says the app's Tier C pass skips them. *Proposed: images only, no GIFs* — an
   animated GIF's first frame is rarely a good face, and it keeps the pass to what the spike measured.
   ~~Confirm?~~ ✅ **Answered 2026-11-19: images only, no GIFs, as recommended.** The Tier C pass
   skips GIFs, which is now the recorded decision the question found missing. (Group E's E2 in
   [albums-stage-3.md](../../design/albums-stage-3.md).)

### References

- [docs/design/albums.md](../../design/albums.md) §6 (the third state), §8 decisions 9, 20, 40, 45
  and 46
- [docs/design/albums-stage-3.md](../../design/albums-stage-3.md) steps 3.2 to 3.4
- [ADR-0022](0022-people-criterion-anchored-to-a-photo.md), [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md),
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md)
- [ADR-0017](0017-composable-album-rule-representation.md) Amendment 4 — how a `people` clause reads
  these states
- `data/MediaIndexDatabase.kt`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`

## Amendment 6 — 2026-11-20 — Tier C survives a re-extraction of an unchanged file, and a rollback journal that keeps page copies is not scrubbed

> **Accepted by the maintainer on 2026-11-20**, as the resolution the Reviewer of step 3.2 suggested
> for two of his findings ([albums.md](../../design/albums.md) §8 decision 48). Written by the
> Planner. Nothing below is built yet: the *3.2 follow-up* in
> [albums-stage-3.md](../../design/albums-stage-3.md) builds it.

> **Built 2026-11-20**, as the *3.2 follow-up* briefed it, and verified by the definition of done —
> the gate is in [albums-stage-3.md](../../design/albums-stage-3.md), *3.2 follow-up*, *Built and
> verified*. §1 is `MediaIndexStore.kt`'s one private test, `keepsTierC`, on the healing key, used by
> `upsert` and `healMediaUri` in both stores; §2 is `deletionIsScrubbed` and the two mode reads in
> `MediaIndexDatabase.kt`, which `RoomMediaIndexStore.deleteAllFaceData` applies to the connection
> it deleted on. Nothing above was rewritten. **Not proven by it:** each phone's real journal and
> locking modes, and so whether *Revisit when* below has fired; that a favourite toggle advances
> `generationModified` and leaves Tier C intact — still expected, not observed; `exclusive` locking
> at runtime, which only the pure mapping covers; and a URI written twice in one `upsert` call, whose
> order can only drop Tier C, never keep it wrongly. The first two are step 3.7's.

This amendment **reverses one rule of Amendment 5**, adopted the day before — the first bullet of
*Keeping Tier C true* — and **qualifies** what its *Deleting face data* lets a deletion report. Both
places are annotated above and left intact. Everything else in Amendment 5 continues to govern: the
tables, the two stamps, the per-image states, the conditional write, `secure_delete` and the
checkpoint. It is an amendment rather than a superseding ADR for the reason ADR-0007 Amendment 2
gives when it reverses a point of its Amendment 1: the structural decision has not stopped applying,
and what changes is one invalidation condition and the meaning of one returned flag.

### 1. Tier C is dropped by a changed file, not by a re-extraction

**What was wrong.** Amendment 5 read "Tier A re-extracts an image" as "the file changed". Tier A
does not re-extract only changed files. On API 30+ it selects rows by `MediaStore`'s generation
(Amendment 1), which `MediaStore` advances on any update to a row, metadata-only ones included — a
favourite toggle is a write to `IS_FAVORITE` ([ADR-0011](0011-favourites-backed-by-mediastore.md))
and is expected to advance it, **not verified on a device**. And a volume whose `MediaStore` version
changes is reselected in full. Step 3.2 built the rule as written — `MediaIndexStore.upsert` drops the
Tier C rows of every URI it writes — so each favourite toggle would send that image through detection
and embedding again, and a version resync would delete every face on the volume: of the order of
minutes per thousand photos on the one phone measured
([albums-stage-3.md](../../design/albums-stage-3.md), *Spike outcome*), with every people album
waiting until the pass caught up.

**Decision.**
- When an index row is written over a stored row at the same URI, that image's Tier C rows are
  dropped in the same transaction **if and only if the incoming row's healing triple —
  `displayName`, `size`, `dateModified`, the one `MediaIdentity` of `data/MediaIdentity.kt` — differs
  from the stored row's.** With the triple unchanged, its state and faces stay exactly as they were,
  pipeline and grouping versions included, while the Tier A columns are rewritten as before.
- A written URI with no stored row has no Tier C to keep, and any found under it are dropped, as
  before.
- **The pipeline-version rule does not change.** The row write never reads or writes a pipeline
  version. A state stamped with an older one is pending by Amendment 5's table whether or not Tier A
  touched the row; an unchanged-triple re-extraction leaves it pending, and the Tier C pass's next
  write replaces it. A changed triple drops it, which is pending too. The grouping version likewise.
- **A heal applies the same test.** `healMediaUri` re-keys and keeps the moved Tier C rows only when
  the healed row's triple equals the stored source row's; otherwise the target ends with no Tier C
  rows, its own included, as before. The reconciler heals only through the healing triple, so every
  heal it makes passes. The test is there so that one rule holds for every path that writes a row,
  in the store rather than in its callers. *This point is the Planner's, 2026-11-20, from the
  Reviewer's finding on a test that heals with a changed name; the maintainer did not rule on it and
  can reverse it.*

**Why the triple.** It is the app's single definition of "the same file", and
[ADR-0020](0020-automatic-albums-resolve-against-live-media.md) forbids a second. Tier C already uses
it the other way round: a face result is written only while the row still has the identity it was
computed from. Using it to invalidate as well means one test decides both whether a result may be
written and whether a stored one is still true.

**What it costs.**
- An edit that keeps the name, the byte size and the modification second keeps its old faces. That
  is the blind spot membership healing ([ADR-0010](0010-album-membership-dual-key-identity.md)) and
  the conditional write already accept. Not measured.
- A rename drops Tier C although the pixels did not change, because the name is in the triple: one
  image's inference again. Accepted rather than comparing two of the three fields, which would be a
  second definition of identity.

**Alternatives considered.**
- **Keep the rule, and have Tier A skip a metadata-only change.** Rejected: Tier A could only tell
  the cases apart by comparing the same triple, and it would change Tier A's selection, which
  Amendment 1 governs, to fix a Tier C cost.
- **A content hash per image.** Rejected: a full file read per re-extracted image is the cost this
  amendment removes.

### 2. What a deletion may report as scrubbed

**What was wrong.** Step 3.2 sets `secure_delete` rather than assuming it, but left the journal mode
of a non-WAL database to the platform: Room uses a rollback journal on a low-RAM device, and the
framework then applies its configured default, `TRUNCATE` on AOSP. Outside WAL,
`wal_checkpoint(TRUNCATE)` is a no-op whose first column reads 0, so the deletion reported scrubbed
whatever the rollback mode was. SQLite's documentation is explicit about the mode that matters: under
`PERSIST` the journal is not deleted at commit, only its header is zeroed, so the pages the deletion
changed — the face rows as they were — stay in `media_index.db-journal`. `journal_size_limit` bounds
how much is kept, not whether. `secure_delete` zeroes the database file's pages, not the journal's
copies of them.

**Decision.** `deleteAllFaceData` reads the journal mode, and the locking mode, of the connection its
transaction ran on, and reports scrubbed only as this table allows:

| `journal_mode` | Scrubbed when | Why |
|---|---|---|
| `wal` | the `TRUNCATE` checkpoint's first column is 0 — unchanged | the log is emptied and truncated |
| `delete` | the locking mode is `normal` | the journal is deleted at commit |
| `truncate` | the locking mode is `normal` | the journal is truncated to zero length at commit |
| `memory`, `off` | always | no journal file is written |
| `persist` | never | the header is zeroed, the page copies are kept |
| anything else | never | fail closed |

The locking-mode condition is there because SQLite documents that in `exclusive` locking mode a
rollback journal may also remain after commit. Android does not open its databases that way, so it
costs nothing where it is not needed.

- **Not scrubbed is a result, not an error.** The rows are deleted and committed either way. A thrown
  error would tell the caller nothing was deleted, and on a device whose journal mode never changes a
  deletion that refused to run would never delete at all — face data kept for good, which is worse
  than page copies left in a private file that later transactions overwrite. A `secure_delete` that
  does not read back still stops the deletion, as step 3.2 built it: that is a setting the next
  attempt makes again, where a journal mode is the platform's and is the same on every attempt.
- **What the caller does with not scrubbed** is what step 3.2 already said for a blocked checkpoint:
  the face data is deleted as far as the app is concerned and the switch stays off, and the deletion
  is repeated at each later process start while face data is off — not only while a Tier C row
  exists, which is the narrower condition
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) §3 states and is annotated there. On a
  `PERSIST` device every repeat reports not scrubbed again; that is accepted, below. How Settings
  presents it, if at all, is step 3.6's.
- **No journal mode is set when the database opens.** Considered and rejected: the mode is Room's
  and the platform's choice, made per device and applied to every connection the framework opens, so
  an override would change every index write on that device to fix a property needed only at
  switch-off, would have to be re-applied for each connection, and would still need this check at the
  deletion — as `secure_delete` is set at open and checked again. SQLite also refuses a journal-mode
  change inside a transaction, so it cannot be pinned to the write connection the way `secure_delete`
  is. No device is known to use `PERSIST`.

**Revisit when** a device is found whose index uses `PERSIST`, or `exclusive` locking: step 3.7
records each phone's actual modes. Leaving the mode alone would then be a new decision, not a
detail — switching that connection out of `PERSIST` for the deletion is the obvious candidate.

### Consequences

**Positive**

- A metadata-only change that leaves the triple alone — a favourite toggle is expected to, not
  verified — no longer costs a face pass; a version resync no longer deletes face data.
- A deletion's scrubbed result is true of the files on every journal mode SQLite has, and false
  rather than optimistic for one it does not recognise.

**Negative**

- Stale faces after an edit that keeps the triple, as above.
- A deletion on a `PERSIST` device never reports scrubbed, and the residue stays until later
  transactions overwrite the journal.
- One more pragma read per deletion, and one more stored-row read per row written, inside the
  transaction that already writes it.

### References

- [docs/design/albums.md](../../design/albums.md) §8 decision 48
- [docs/design/albums-stage-3.md](../../design/albums-stage-3.md), *3.2 — Tier C schema and store*:
  *Reviewed* and *3.2 follow-up*; step 3.7
- [ADR-0010](0010-album-membership-dual-key-identity.md), [ADR-0011](0011-favourites-backed-by-mediastore.md),
  [ADR-0020](0020-automatic-albums-resolve-against-live-media.md),
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) §3
- SQLite, *PRAGMA Statements*: `journal_mode`, `journal_size_limit`, `wal_checkpoint`,
  `secure_delete` — read 2026-11-20
- `data/MediaIndexStore.kt`, `data/MediaIndexDatabase.kt`, `data/MediaIdentity.kt`,
  `data/MediaIndexTierAIndexer.kt`

## Amendment 7 — 2026-11-20 — Step 3.3's storage: attempts in Tier C and version 6, a per-installation preferences file, a fourth backup section (adopted 2026-11-20)

> **Adopted 2026-11-20, when the maintainer approved it**, together with the dated notes it carries
> in [ADR-0014](0014-album-database-included-in-auto-backup.md),
> [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) and
> [ADR-0024](0024-face-data-disclosure-and-delete-switch.md). Drafted by the Planner at step 3.3's
> refinement ([albums-stage-3.md](../../design/albums-stage-3.md), *3.3 — Face detection and
> embedding*), and labelled *proposed* in this record until then, as Amendment 5 was, because the
> instructions' status vocabulary has no state for a draft; this record's `Active` status covered
> the Decision and Amendments 1–6 throughout, and now covers this amendment too. **Nothing below was
> built before adoption.** Step 3.3's Task 1 builds §1 to §5; it waited for approval because §1 is a
> migration and §3 a new preferences file, and once either has run on a device it is undone only by
> another change. The marker that stood under the record's title while this was proposed was removed
> on adoption.
>
> **Written late** — kept on adoption as the record of how this amendment came to be. The refinement of 2026-11-20 cited this amendment before it existed; this text
> is what that refinement decided, with its reasons. It adds to the plan only in three places: §1
> makes the bound precise and says which stops clear an attempt, and §2 names the malformed reading
> as F1's residual risk. The plan was updated to match.
>
> **Built 2026-11-20**, as step 3.3's Task 1 briefed it, and verified with the rest of step 3.3 by
> the definition of done — the gate is in [albums-stage-3.md](../../design/albums-stage-3.md),
> *3.3*, *Built and verified*. §1: `media_index_face_attempt`, `MIGRATION_5_6` and `6.json`, the
> bound as `FACE_ATTEMPT_BOUND`, in `data/MediaIndexEntities.kt`, `data/MediaIndexDatabase.kt` and
> `data/FaceIndexPass.kt`. §2 and §3: the fourth section and `face_device_state.xml` in
> `res/xml/backup_rules.xml` and `data_extraction_rules.xml`, held to one set by
> `BackupRulesParityTest`; the store is `data/FaceDeviceStateStore.kt`. §4: `writeFaceIndexResults`
> and `recordFaceIndexAttempt` in `data/MediaIndexStore.kt`, both reading the setting inside their
> transaction. §5: `FaceDataStartupDeletion` in `data/FaceIndexPass.kt`, run first by
> `MediaIndexingWorker`. The pass these serve is dormant until step 3.6 records the disclosure as
> shown. F1's residual is unchanged.

This amendment **adds** to the record; it corrects nothing in the Decision or in Amendments 1–6. It
is an amendment rather than a new ADR for Amendment 1's reason: the index is still the one database
for Tier C, and what follows applies existing rules — Amendment 5's Tier C lifecycle, Amendment 2's
line between the index and a preferences file, the Decision's backup exclusion — to what step 3.3
needs. It answers what Amendment 5 left to 3.3 (the bound, and how attempts are counted), what
[ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) §3 and
[ADR-0024](0024-face-data-disclosure-and-delete-switch.md) §2 left to 3.3 (where per-installation
state lives), and open question F1 (a fourth backup section). What is not here — the pass's
parameters, its budget, its order in the worker, the Tier C lock, the capability record's details —
is the plan's, under *Decided at 3.3*, and reversible there.

### 1. Attempts are counted in a third Tier C table, and the index reaches version 6

**Why attempts must be persisted.** Amendment 5 says a failure a retry could cure writes nothing,
and an image that fails that way every time is recorded failed after a bounded number of attempts.
The failures that need the bound are the ones that end the process — a crash in OpenCV's native code
([ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md), *Negative*), or running out of
memory — after which nothing can be written. So an attempt is counted **before** the work it counts,
and only a persisted count survives to the next run.

**Decision.**
- **A table, `media_index_face_attempt`:** one row per image with an attempt under way or cut short —
  the image's index-row key, the pipeline version, and a count. Nothing else, and no face data.
- **Recorded before the image is decoded**, in its own transaction, and only while the index row
  still has the identity the pass read — the same condition as a result write. Under another
  pipeline version the count restarts at 1.
- **Cleared** by the image's result write, in the same transaction, and by every stop the image is not
  to blame for: cancellation, face data switched off, the pass's time budget, a missing permission, and
  a file that cannot be opened, which reconciliation will deal with. It stays counted only when the
  attempt ends with the process, or with an exception the pass does not handle.
- **The bound is three.** An image is decoded at most three times under one pipeline version; a
  candidate found with three attempts already recorded is written failed without being decoded.
  *Why three:* a failed image is not retried until the pipeline version changes (Amendment 5), so a
  bound of one would lose the faces of an image that only met a bad moment — memory pressure from
  another app — for good; and because the pass resumes in URI order, an image that kills the process
  is met again first in the next run, holding back every image after it, so each extra attempt costs
  one more lost run. Three is the Planner's choice, not a measurement.
- **Every Tier C rule applies to it:** dropped with the image's other Tier C rows on a changed healing
  triple (Amendment 6), re-keyed by a heal, deleted with its row by reconciliation, emptied by the
  face-data deletion in the same transaction, and counted by "is there face data". *Why:* an attempt
  for a changed file is an attempt on a different file, and one kept across a deletion would carry a
  switched-off past into the next switch-on. It is not face data — no embedding, crop or group — and
  is kept with Tier C for its lifecycle, not its content.
- **Why a table of its own.** Not an outcome on `media_index_face_state`: Amendment 5's table defines
  pending as no state row or an old version, so a state row for an image in progress would make every
  reader of states exclude it. Not a column on the image row: rejected in Amendment 5 for every Tier C
  field. Not a preferences file: it is per image, and the index's correctness depends on it — it is
  what separates waiting from failed — which is Amendment 2's test for the index database.
- **`Migration(5, 6)` creates the table, empty, and changes nothing else**, registered beside the four
  earlier migrations with no destructive fallback; its SQL is `6.json`'s, as `Migration(4, 5)`'s is
  `5.json`'s. The index reaches version 6, and `6.json` is committed by the change that produces it.
  The version reached is the `@Database` annotation's.

### 2. A fourth backup section — open question F1

**What was read**, on Android's Auto Backup page on 2026-11-19 and again on 2026-11-20: Android 16
QPR2 (API 36.1) added `<cross-platform-transfer platform="ios">` to `data_extraction_rules.xml`; a
transfer mode with no rules "is fully enabled for all content except for `no-backup` and `cache`
directories"; and the section must contain `<platform-specific-params>` naming the app on the other
platform, whose ids "guarantee that data is only transferred *to* the specified app". This app has no
iOS counterpart and its file has no such section.

**Not established:** whether a transfer to iOS happens at all for an app with no section, and how a
36.1 device reads a section without the params — ignored, honoured, or treated as malformed.

**Decision — conservatively:** the section is added, holding **the same `<exclude>` lines as the
other two**, no `<include>` and no `<platform-specific-params>`. *Why:* [albums.md](../../design/albums.md)
§8 decision 9's "never enters a backup" cannot rest on an unverified reading of the platform. An
exclusion in a mode that never runs is inert; a missing one in a mode that does run sends face data
to another device. No params, because there is no iOS app to name, and invented ids would declare one.
`<exclude>` only, as [ADR-0014](0014-album-database-included-in-auto-backup.md) requires. "Three
places", in this record and in ADR-0014 and ADR-0024 §6, becomes **four**: `backup_rules.xml` and the
three sections of `data_extraction_rules.xml`. It lands in Task 1, so it ships with the change that
first writes face data.

**Residual, open:** the malformed reading is the one case in which the section would be worse than
none, and no 36.1 device is available here — the SM-G990B runs 36.0. Removing the doubt by
construction, by moving `media_index.db` to the no-backup directory that every mode excludes, would
be a change of its own and a new amendment. *Recommended: not now; revisit if a 36.1 device is found
to misread the section.* It is the maintainer's to confirm and blocks nothing
([albums-stage-3.md](../../design/albums-stage-3.md), F1).

### 3. Per-installation face state is a preferences file, `face_device_state`, excluded from backup

**Decision.** A new preferences file, `face_device_state`, holds two things: whether the face-data
disclosure has been shown ([ADR-0024](0024-face-data-disclosure-and-delete-switch.md) §2), and the
capability check's persisted result
([ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) §3). It is excluded from backup in all
four places, `<exclude>` only. The face-data setting stays where ADR-0024 §1 put it: a scalar in
`app_settings`, backed up. What the capability record holds is the plan's (*Settled before 3.3*,
issue 2).

*Why a preferences file and not this database* — Amendment 2's two tests, in order. It is not
structured: a flag and a few scalars. And the index's correctness does not depend on it: a lost flag
shows the disclosure again, a lost record repeats the check. Inside the database it would cost a
migration for three scalars, and inside Tier C the face-data deletion would empty it and bring the
disclosure back on switching on again, against ADR-0024 question 4.

*Why not `app_settings`:* it is backed up, and both values belong to this installation. A restored
"shown" would skip the disclosure on a new phone, where face indexing starts again from nothing
(ADR-0024 question 1); a restored capability record is another device's answer. *Why not
`media_index_run_state`:* one store per file, and that file reports on a job.

*What it costs:* an `AGENTS.md` call-out, not an approval — a row in *Preference stores*, and both a
`SharedPreferences*` and an `InMemory*` store.

### 4. A Tier C result write reads the setting inside its transaction

`writeFaceIndexResults` takes the face-data setting as a parameter and reads it inside its
transaction; with the setting off it writes nothing and returns 0. *Why in the store:* ADR-0024 §3
makes the setting a backstop "in the same transaction", and a store rule holds for every writer, not
only the pass. It is a backstop and not the guarantee, because a `SharedPreferences` read is not part
of the SQLite transaction; the guarantee is ordering — the switch persists *off*, then waits for the
pass — which the plan builds as a process-wide lock and step 3.6 uses.

### 5. The start-up deletion — what the caller does with its result

Amendment 6 §2 said a deletion that is not scrubbed is a result, repeated at each process start while
face data is off. **Decision**, splitting the caller between steps 3.3 and 3.6:
- **The worker runs it first, before Tier A, whenever the setting reads off** — not only when a Tier C
  row exists (ADR-0024 §3 as Amendment 6 annotates it). Step 3.6 adds the same call at app open.
- **Not scrubbed:** no retry within the run, nothing persisted, nothing shown by 3.3. Whether Settings
  presents it is 3.6's.
- **A thrown error** — `secure_delete` not reading back, an SQLite error — is caught, never fails the
  run or stops Tier A, and is repeated at the next start. *Why:* failing the run for a face-data
  problem would also stop region and time albums from being indexed, and the repeat at start-up is
  already what ADR-0024 §3 relies on.
- **It logs at most that it ran and whether it scrubbed** — no count and no exception message
  (ADR-0024 §6).

### 6. URI spellings — not a defect, and where the invariant lives

Tier C keys, and Amendment 6's test, match URIs exactly, so two spellings of one image in the index
would give it two face results. **Checked in the code on 2026-11-20: no install holds two** — every
production writer stores the `content://media/external/…` spelling, which the row-delta provider
canonicalises at the source; the evidence is in the plan (*Settled before 3.3*, issue 1).
**Decision: nothing is built.** The invariant is the provider's, not the store's: tests seed
per-volume spellings on purpose, and enforcing one in `upsert` would break them for no production
case. *Recorded here* so that it is not "fixed" in the store later. If a writer ever stores another
spelling, the repair is Amendment 3's kind: a data migration to the canonical one.

### Still open, and not this amendment's

- **F1's residual**, §2 — the maintainer's; blocks nothing.
- ~~**F2, the OpenCV version** — [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md)'s;
  blocks step 3.3's Task 2, not Task 1 and not this amendment.~~ **Answered 2026-11-20 by the
  maintainer: 4.13.0**, the build the spike measured — recorded in ADR-0023 §2.
- **ADR-0023's verify items** 1, 2, 4, 5, 6 and 8 at step 3.3, and 7 at 3.7 — unchanged.

### Consequences

**Positive**

- An image that kills the process cannot keep people albums waiting for ever, nor hold back the rest
  of the library for more than three runs.
- Face data is excluded from every transfer mode Android documents, whatever the answer to F1.
- The disclosure and the capability answer are true of the device they are on.

**Negative**

- One more table, one more migration, and a write per image before its decode.
- A second excluded preferences file, and a fourth place in which every exclusion must be written
  correctly — a mistake still invisible except to a reading of the XML, or to the test the plan adds.
- An image that fails three times through bad luck is failed until the next pipeline-version bump.

### References

- [docs/design/albums-stage-3.md](../../design/albums-stage-3.md), *3.3 — Face detection and
  embedding*: *Settled before 3.3* and *Decided at 3.3*; open questions F1 and F2
- [docs/design/albums.md](../../design/albums.md) §8 decision 9
- [ADR-0014](0014-album-database-included-in-auto-backup.md),
  [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) §3,
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) §1–§3 and §6 — each with a dated note
  pointing here
- <https://developer.android.com/identity/data/autobackup> — *Configure Cross-Platform Transfers*,
  read 2026-11-20
- `data/MediaIndexStore.kt`, `data/MediaIndexDatabase.kt`, `data/MediaIndexingWork.kt`,
  `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`

## Amendment 8 — 2026-11-20 — ~~Each face's pixel size is stored, so that a group's representative is a usable face, and the index reaches version 7~~ (adopted 2026-11-20; **reversed 2026-11-20, before anything was built**)

> **Reversed 2026-11-20, by the maintainer — his words "let's go with your recommendations", given to
> the Planner's recommendation after reading the measurement below.** Step 3.4's Task 2 built §3's
> size-aware representative in `data/FaceGrouping.kt` and measured it on the SM-G990B over the real
> library ([albums-stage-3.md](../../design/albums-stage-3.md), *3.4*, Task 2): **21 of the 104
> offered groups have no member at least 60 px across**, so the rule has nothing better to choose for
> them, and it fixes **1 of the 22** representatives under 60 px — 83 of 104 offered representatives
> are at least 60 px with the rule, 82 of 104 with the plain medoid; the median stays 108 px and the
> smallest 40 px. A permanent schema change — a seventh index version, a sixth migration and a column
> on every face — is not worth one thumbnail.
>
> **What was built and what was not.** No `Migration(6, 7)`, no column and no `7.json` were ever
> built; the index stays at **version 6**, and no install holds a row this amendment describes. The
> size-aware rule exists only in Task 2's uncommitted `data/FaceGrouping.kt`, and step 3.4's Task 2b
> removes it. *Removed: step 3.4 was built and verified on 2026-11-21 with the plain medoid, no
> size-aware code in `app/src/`, and the index at version 6 (Amendment 5's Built notes).*
>
> **What remains of it: nothing.** A group's representative is its plain **medoid**, ties within the
> tolerance to the lower face id, as Task 1 built; `FACE_REPRESENTATIVE_MIN_SIDE_PIXELS` (60) and the
> unknown-size rule (§2) are dropped with the column; `FACE_GROUPING_VERSION` stays 1 (§3's bump to 2
> named a rule that no longer exists). Small thumbnails — 22 of 104 under 60 px — are accepted as the
> picker's behaviour for now ([albums.md](../../design/albums.md) §8 decision 50, amended the same
> day); a different fix would be a new decision. The headings and decisions below are struck rather
> than deleted because their reasoning is the record of why a size was wanted and why it was dropped.

> **Adopted 2026-11-20, on the maintainer's approval** — his words "Let's go with your
> recommendations", given to the Planner's recommendation of option (i) once step 3.4's Task 1 had
> measured the representatives: a `Migration(6, 7)`, an amendment to this record, and a
> representative that prefers a face at least 60 px across ([albums.md](../../design/albums.md) §8
> decision 50). Written by the Planner the same day, after the approval and from the recommendation
> as it was put. The column, the rule for existing rows and the grouping-version bump below are the
> Planner's within that approval, each with its reason; **the maintainer can reverse any of them** —
> as a text edit until step 3.4's Task 3 has run the migration on a device, and only by another
> change after. ~~This record's `Active` status covers the Decision and Amendments 1–8. **Nothing below
> is built yet:** Task 2 builds §3, Task 3 builds §1, §2 and §5
> ([albums-stage-3.md](../../design/albums-stage-3.md), *3.4*, *Tasks*).~~ *Reversed the same day,
> above: Task 2 built §3 and Task 2b removes it; §1, §2 and §5 were never built. Task 2b removed it
> before step 3.4 was verified on 2026-11-21.*

This amendment **adds** to the record; it corrects nothing in the Decision or in Amendments 1–7. It is
an amendment rather than a new ADR for Amendment 1's reason: the index is still the one database for
Tier C, and what follows applies Amendment 1's precedent — bump, migrate, commit the JSON — to one more
fact on Amendment 5's face row. It reverses a choice of the plan's, not of a record: step 3.4's
refinement had decided on no migration and the plain medoid as representative.

### What was measured

Step 3.4's Task 1 on the SM-G990B, over the real library — 422 faces, 104 groups with at least two
distinct images: **22 of the 104 representatives**, each its group's medoid, have a face **under
60 px** on the shorter side of the decoded frame; 35 are under 80 px, the median is 108 px and the
smallest 40 px. The plan had said that more than a few would put a stored size to the maintainer; 22
is a fifth of the picker. The spike's rule — the medoid among members at least 60 px across — had been
set aside at the refinement only because no size was stored: a stored box is in fractions of the
upright frame ([ADR-0022](0022-people-criterion-anchored-to-a-photo.md) §1), the decoded size is not
kept, and Tier A's EXIF width and height are often absent and not upright.

### ~~1. One column on the face row: the box's short side in pixels~~ (reversed; never built)

~~**Decision.**~~ *Withdrawn:* `media_index_face` gains **`boxShortSidePx INTEGER NOT NULL DEFAULT 0`**: the shorter of
the box's width and height **as detected**, in pixels of the upright decoded frame (long side at most
`FACE_DECODE_LONG_SIDE`), before the box is clamped to the frame, rounded down. The Tier C pass writes
it with the face, from the `RawFace` it already holds, and nothing else writes it. A stored face is at
least `FACE_MIN_SIDE_PIXELS` (40) on both sides, so every real value is at least 40 and **0 means
unknown**.

*Why per face, and not the decoded width and height per image* — which is the more primitive fact:
- **The same measure as the cut-off.** Decision 46's 40 px cut-off is judged on the detected box in
  the decoded frame, unclamped (`faceDetectionOf`). A stored fraction times the decoded size gives the
  box after clamping, which is smaller for a face cut by the image's edge, so 60 would be judged on
  another measure than 40.
- **What its reader needs.** Grouping reads faces by id, ids and embeddings; one integer from the same
  row joins nothing. Width and height on `media_index_face_state` would need a join by image key for
  every face, and two columns on every processed image, most of which have no face.
- **An integer, rounded down**, because both thresholds are whole pixels: ⌊x⌋ ≥ 60 exactly when
  x ≥ 60.
- Nothing else needs the decoded size: step 3.6's crop decodes the image at display time and knows
  its size then. If something later does, it is a column added then.

ADR-0022 is unchanged: an anchor stores a box in fractions, and the pixel size never enters the album
database.

### ~~2. Rows written before it: unknown, and never treated as large~~ (reversed; never built)

~~**Decision.**~~ *Withdrawn:* `Migration(6, 7)` adds the column with its default and changes nothing else — no row
deleted, no state or attempt cleared, `FACE_PIPELINE_VERSION` unchanged. A face written by a build at
index version 6 reads 0, unknown, and **unknown counts as under 60 px**: the representative rule (§3)
passes over it, and a group with no face known to be at least 60 px falls back to its medoid — the rule
Task 1 measured, so no group is worse off than before this amendment. Such a face gains a size only
when it is detected again: when its file changes (Amendment 6) or at the next pipeline-version bump.

*Why not force those faces to be detected again:*
- **No install holds them.** The only install is the dev phone (the plan's *The dev-device route*), on
  which no face data is persisted: the pass is dormant until step 3.6's disclosure, and 3.3's and
  3.4's measurements ran on in-memory indexes or delete what they wrote. The rule is for a build that
  had faces, and for such a build the cost of the fallback is a medoid thumbnail, not a wrong one.
- **Clearing Tier C inside a migration would delete face data outside the deletion's guarantees.**
  `secure_delete` is set when the index opens (Amendment 5, *Deleting face data*); a migration's
  `DELETE` may run before that, leaving the old bytes in free pages that no later switch-off scrubs.
- **A pipeline-version bump would misname the change.** The pipeline computes the same boxes and
  embeddings; a bump would decode every image again to choose among faces already stored.

### ~~3. The representative rule that uses it~~ (reversed; built by Task 2, removed by Task 2b)

~~**Decision.**~~ *Withdrawn — the representative is the plain medoid:* A group's representative is its **medoid** — the member whose summed cosine distance to
the other members is smallest, a tie within the medoid tolerance going to the lower face id — **unless
the medoid's short side is under 60 px**. Then it is the member with the smallest summed distance among
those whose short side is at least 60 px, ties as before: the first usable face in medoid order. If no
member is at least 60 px, or none is known, it is the medoid. The sums stay over the whole group, so
the representative is the usable face most typical of the whole group, not of the usable ones. The
60 px threshold is a named constant beside the grouping cut, `FACE_REPRESENTATIVE_MIN_SIDE_PIXELS`.

- *Why 60:* it is the spike's rule, which the approval restores. The 40 px cut-off decides whether a
  face can be matched at all; 60 px is about whether a thumbnail cropped from it is recognisable. It is
  a judgement, not a measurement of recognisability.
- **Membership is unchanged** — which faces form a group never depends on size; only which member
  stands for it.
- ~~**`FACE_GROUPING_VERSION` becomes 2.**~~ *Reversed: it stays 1.* `groupId` is the representative's face id (the plan's
  *Decided at 3.4*), so a changed representative rule changes stored group ids, and the constant names
  that rule. Version 1 named the plain medoid Task 1 built; no build has written it, grouping not being
  wired, so the bump regroups nothing and keeps the constant meaning what it names. The component split
  of decision 50 changes how groups are computed and not what — the same groups and representatives —
  so it bumps nothing by itself.

### ~~4. Every existing rule applies, unchanged~~ (moot: nothing is stored)

- **Recomputable cache.** It is derived from the face's detection and recomputed by detecting again.
- **Deletion.** It lives on face rows, so `deleteAllFaceData`, which deletes those rows under
  `secure_delete`, removes it with nothing else to touch; a changed healing triple drops it with its
  face (Amendment 6); a heal keeps it with the face's id; reconciliation deletes it with the image.
- **Backup.** The index database is excluded in all four places (Amendment 7 §2); there is no new file.
- **Writes and logs.** It is written in the face's own write, under Amendment 7 §4's setting read, and
  is never logged ([ADR-0024](0024-face-data-disclosure-and-delete-switch.md) §6).

### ~~5. The migration~~ (reversed; never built — the index stays at version 6)

`Migration(6, 7)` is `ALTER TABLE media_index_face ADD COLUMN boxShortSidePx INTEGER NOT NULL DEFAULT 0`,
its SQL `7.json`'s, registered beside the five earlier migrations with no destructive fallback. The
entity declares the same default, so that Room's validation of the migrated schema matches. ~~The index
reaches version 7, read from the `@Database` annotation; `7.json` is committed by the change that
produces it.~~ *Reversed: no migration, no `7.json`; the index stays at version 6.*

### Consequences

*As adopted, and moot since the reversal; the reversal's own cost is the Reversed note's — 22 of 104
thumbnails under 60 px, accepted.*

**Positive**

- ~~Where a group has a usable face, the picker shows it — the 22 groups in 104 measured, at most.~~
  Measured by Task 2: 1 of the 22.
- Groups are unchanged, and no image is decoded again.

**Negative**

- ~~A seventh version and a sixth migration, for one integer per face.~~
- ~~A face stored before version 7 is unknown until detected again, and its group keeps the medoid.~~
- 60 px is a judgement; a thumbnail at 60 px may still be hard to recognise.

### References

- [docs/design/albums-stage-3.md](../../design/albums-stage-3.md), *3.4 — Clustering and the person
  record*: *Decided at 3.4* and *Tasks*
- [docs/design/albums.md](../../design/albums.md) §8 decisions 46 and 50
- [ADR-0022](0022-people-criterion-anchored-to-a-photo.md) §1,
  [ADR-0023](0023-face-pipeline-models-runtime-and-delivery.md) §5,
  [ADR-0024](0024-face-data-disclosure-and-delete-switch.md) §6
- `data/FaceGrouping.kt`, `data/FaceIndexPass.kt`, `data/MediaIndexEntities.kt`,
  `data/MediaIndexDatabase.kt`
