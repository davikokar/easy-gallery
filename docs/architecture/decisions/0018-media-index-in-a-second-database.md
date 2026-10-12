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
