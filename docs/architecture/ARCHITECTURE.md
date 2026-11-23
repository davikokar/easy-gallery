# Easy Gallery — Architecture

Easy Gallery is a native Android image and video gallery app built entirely with Jetpack Compose.
It browses the media already present on the device via the Android `MediaStore`, organised by
folder, and lets the user view, sort, group, filter, search, and manage that media. It keeps no
copy of the user's media. It owns **two** small Room databases — albums, and the media index —
and neither holds media: the first holds references and user intent, the second holds facts
extracted from media that can be recomputed from the device at any time.

- **Package:** `com.davide.seddio.easygallery`
- **Language:** Kotlin · **UI toolkit:** Jetpack Compose (Material 3)
- **SDK levels and dependency versions:** [`app/build.gradle.kts`](../../app/build.gradle.kts) and
  [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) own them. Do not restate a
  version here; it will rot.
- **Data sources:** Android `MediaStore` for all media; two Room databases, one for albums and one
  for the media index; `SharedPreferences` for the language override and user preferences. See
  *Persistence* below.
- **Network/account:** none, except Google Play Billing for the optional tip jar.
- **Dependency injection:** none — components are instantiated directly, so tests pass fakes.

> **This document describes structure, not decisions.** Choices with lasting impact are recorded
> as ADRs in [decisions/](decisions/) and indexed in [README.md](README.md). An active ADR is
> binding and outranks this file. Where a section below is governed by one, it says so — follow
> the link rather than inferring the rule from the summary here.
>
> **It is maintained, not archived.** The Planner owns it, and a change that adds a state holder,
> a preference store, a navigation branch, a main view, a `data/` file, or a tech-stack dependency
> must update it in the same change — *after* the work lands, so it describes what was actually
> built. The full trigger list is in
> [the architecture documentation instructions](../../.github/instructions/architecture-docs.instructions.md).
> Section numbering is cited by those triggers, so keep it stable.

---

## 1. The main views

The app has **five main views**, carried by **three screens**. These names are canonical; the full
vocabulary is in
[.github/instructions/ui-vocabulary.instructions.md](../../.github/instructions/ui-vocabulary.instructions.md).

| Canonical name | What it shows | Implementation |
|---|---|---|
| **Folders View** | Every device folder ("bucket") as a grid or list, with a thumbnail and item count. The view the app opens on. | `FolderListScreen` + `DisplayMode.FOLDERS` |
| **Timeline View** | Every image and video on the device, flat across folders, under date headers. | `FolderListScreen` + `DisplayMode.TIMELINE`, body drawn by `CalendarGrid` |
| **Albums View** | Every user-created album as a cover tile, preceded by the Favourites tile where the platform supports it. | `FolderListScreen` + `DisplayMode.ALBUMS`, body drawn by `AlbumsViewBody` |
| **Folder Detail View** | The media inside one folder. | `FolderDetailScreen` |
| **Album Detail View** | The media inside one album. Also renders Favourites, via an `isFavouritesAlbum` flag. | `AlbumDetailScreen` |

Folders View, Timeline View, and Albums View are three modes of one screen, switched by a
`DisplayMode` flag rather than by navigation, because they share the same search, sort, filter,
grouping and selection machinery. Albums View joined them rather than becoming a fourth screen —
**[ADR-0013](decisions/0013-albums-as-third-display-mode.md)**. Folder Detail View and Album
Detail View are separate screens.

The **Main view bar** (`MainViewBar`) switches between those three modes and opens Settings: three
tabs at the bottom of the screen, in the order Folders, Albums, Timeline — deliberately not
`DisplayMode.entries` order — then a fourth item, **Settings**, which is a destination but neither a
main view nor a `DisplayMode`. It is shown on all five main views, including during selection and
search, and on the Settings screen, where the Settings item is selected and no tab is. It is hidden
in the full-screen viewer, on the Folder thumbnail picker and the Album cover picker — the
draft-and-commit modes of the two detail views — on the New album and New automatic album
screens, on the Manage excluded screen and on the Permission denied screen. A tap on any of its four
items cancels every selection and search and leaves any detail screen before switching. It replaced
the title dropdown, which no longer exists, and the three main views' title is now the app's name
beside its logo — **[ADR-0013](decisions/0013-albums-as-third-display-mode.md)** Amendment 2;
Settings as a destination is its Amendment 3, and the Album cover picker joined the hidden list with
**[ADR-0015](decisions/0015-album-cover-persisted-random-member.md)** Amendment 1. Where it sits is in §4. Pressing back from Timeline
View, Albums View or the Settings screen returns to Folders View rather than leaving the app, and
taps on the bar add no history.

> **Naming debt:** `FolderListScreen` and `CalendarGrid` predate the canonical names, and
> `FolderListScreen` now hosts three views rather than two, which makes its name worse than it
> was. A rename to `FoldersScreen` and `TimelineGrid` is proposed but not done. `DisplayMode` has
> been renamed and is no longer part of this debt. Do not introduce a third set of names in the
> meantime.

Beyond the five views there are the **full-screen viewer** (`FullImageScreen`), the **New album
screen** (`NewAlbumScreen`), the **New automatic album screen** (`NewAutomaticAlbumScreen`), the
**Settings screen**, the **Manage excluded screen**, and the **Permission denied screen**.

The New album screen is the manual branch of album creation: the **Album type dialog** offers
Manual and Automatic, and Manual opens a screen that names the album and picks its members before
anything is written —
**[ADR-0016](decisions/0016-album-creation-type-choice-and-picker-screen.md)**. Its body is a
two-level browser rather than a view of its own: folders first, laid out and ordered as Folders
View lays them out but filtered by neither that view's search query nor its media-type filter,
then the media inside the folder the user opens, arranged the way Folder Detail View would
arrange it. Only the media level selects, and the selection accumulates across folders. The
screen reads display preferences and never writes them. Amendment 1 of that ADR records the
reshape and why, and its Corrections record what the picker stopped inheriting.

The New automatic album screen is the automatic branch, live since Stage 2 step 2.7: it names the
album and describes it by browsing rather than typing — a **Place picker** of countries, regions
and places the index found, and a **Date picker** of years and months that have photos, each with
counts filtered by the other's choice — then writes one row whose `rule` holds the choice and no
membership rows. Nothing is written until Create. Since Stage 3 step 3.6 a third tab, the **People
picker**, offers a grid of **Person tiles** — one per person the index grouped, at most
`MAX_PEOPLE_PER_ALBUM` (8) chosen — and the Place and Date counts follow the chosen people; the
People list itself is not cross-filtered. The tab is offered only while face data is on and the
stored capability record for the current key says capable, so an unknown capability hides it (§3,
`AutomaticAlbumPickerState`; **[ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md)**
Amendment 1 §5).
**[ADR-0016](decisions/0016-album-creation-type-choice-and-picker-screen.md)** Amendment 2 owns the
screen and why it is a screen of its own; its draft and lists live in `AutomaticAlbumPickerState` (§3). A rule
cannot be edited once written, only read, in Album Detail View's **Album info dialog**
([albums.md](../design/albums.md) §8 decisions 26 and 31; §11). Each picker
list opens with a collapsible **Status area** — a header and its collapse control over the **Index
band**, and nothing else. The Index band is read from `MediaIndexingViewModel` rather than from the
picker's state (§3) and is drawn only while a pass runs or some live media is not yet indexed;
outside a pass it carries the not-yet-indexed figure Settings shows and offers Index now. When the
band has nothing to draw, the Status area is not drawn either. It never starts indexing by itself
([albums.md](../design/albums.md) §8 decision 27). The picker shows no coverage notice: how much of
the library has no usable date or location is shown only by the two coverage rows of Settings' Media
index area ([albums.md](../design/albums.md) §8 decision 29;
**[ADR-0016](decisions/0016-album-creation-type-choice-and-picker-screen.md)** Amendment 3).

The **Face data disclosure** is a dialog, not a screen: `MainActivity` hosts it over every screen
except the full-screen viewer, where it waits, once per installation while face data is on, the
device capable and the media permission granted. Neither Back nor a tap outside closes it; OK or
Turn off does. Settings carries the rest of face data's surface — the **Face data area**, drawn only
while the capability record says capable, and the **Face licences row** in the Legal section, always
present — **[ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md)** Amendment 1; the
state behind all three is `FaceDataViewModel`'s (§3).

---

## 2. Data flow

```
MediaStore (ContentResolver queries)        Room (easy_gallery.db)      Room (media_index.db)
        │                                           │                            │
MediaStoreDataSource ──▶ MediaRepository         AlbumDao                  MediaIndexDao
FavouritesDataSource                                │                            │
        │                                    RoomAlbumStore ──▶ AlbumStore  RoomMediaIndexStore
        │                                           │                            │
        │                                           │              MediaIndexingWorker (WorkManager)
        │                                           │               Tier A, then the place resolve pass
        │                                           │                            │
GalleryViewModel  ──delegates state to──▶  DisplayPreferencesState    MediaIndexDateTakenState
   (central hub)                            FolderViewPreferencesState
        │                                   MediaViewerState
        │   (+ AlbumsViewModel, which owns AutomaticAlbumPickerState and a second
        │      FolderViewPreferencesState, and FavouritesViewModel, CreateFolderViewModel,
        │      BillingViewModel, MediaIndexingViewModel, FaceDataViewModel as feature ViewModels)
        │
*Screen composables   ← read the ViewModel, bind a PreferenceScope
        │
*Content composables  ← stateless; take plain values + callbacks; what androidTest drives
        │
Components (top bars, dialogs, grid/list items, ZoomableImage)
```

State is exposed as `StateFlow` and derived state is built with `combine`, so the UI recomposes
only when the data it reads changes. Room queries that feed the UI return `Flow`, so its
invalidation tracker drives recomposition through the same mechanism rather than a re-read
trigger.

Albums and media meet only in the ViewModel layer: `AlbumsViewModel` is handed the live
`allMedia` list and resolves stored memberships against it. The database never holds a media row.

The second database — the media index, `media_index.db` — **is in that flow, written on one path
and read on three**. It is *filled* by `MediaIndexingWorker` under WorkManager, in two passes per
run: Tier A extraction, then the **place resolve pass**, which fills the derived place columns from
the coordinates Tier A stored, against the bundled gazetteer, and opens no media file. Behind
gates — face data on, the device capable, the disclosure shown, which `FaceDataViewModel` records
since step 3.6 — two Tier C passes follow, the face pass and grouping (§3,
`MediaIndexingWork.kt`; §6). It is *read* by
`MediaIndexDateTakenState`, a `GalleryViewModel` delegate that projects captured date-taken by media
URI so `SortType.DATE_TAKEN` can sort by it; by `MediaIndexingViewModel`, which also schedules the
work and reports progress, last-indexed state and the two coverage figures to the Settings screen
and, to the New automatic
album screen's Index band, whether a pass is running and how many live items are not yet indexed;
and by `AlbumsViewModel`,
which evaluates automatic-album rules against it and counts the New automatic album screen's
entries over the same data (§3).

**The worker runs in the app's own process, on no screen's lifecycle, and writes through the same
`EasyGalleryApp` index singleton every reader observes.** That is load-bearing. Re-evaluating
automatic albums after each index batch has no trigger of its own: the worker's writes invalidate
the shared Room database, the index `Flow` re-emits, and `AlbumsViewModel` recomputes — no
WorkManager callback is involved. Moving the worker into another process, or building a second
instance of the database, would still compile and pass every unit test, and automatic albums would
silently stop updating after a batch until the next app open. This paragraph used to call the worker
"out of process with any screen", which read as the opposite.

All three read paths obey the same rule albums do — index keys resolved against live media in the
ViewModel layer, never rendered straight from the database —
**[ADR-0020](decisions/0020-automatic-albums-resolve-against-live-media.md)**. Date-taken sorting
joins the projection onto the live media list it is already sorting, `MediaIndexingViewModel`'s
counts — the Settings figures, one of which is the Index band's not-yet-indexed count — are
intersected with live media before they are counted, and rule evaluation joins index rows to live
media through the shared `LiveMediaLookup` — so a deleted photo stops being counted, and leaves an
automatic album, without waiting for a scan. That last join is built **once per emission** of the
index's evaluation projection or of the live media list, as a `MediaIndexLiveJoin`, and every rule
album and the picker's counts read the same instance — which is what makes a picker count equal the
member count of the album it would create. A people album also reads a second shared flow, the face
evaluation snapshot with the people availability (step 3.5; §3), and resolves its anchor and members
through that same join; a face or grouping write re-emits it the same way a batch does.

**Kotlin property initialisers run top to bottom.** A derived `combine(...)` flow must be declared
below every property it reads, or the class does not compile. Do not work around this with
`lateinit` or a nullable backing field.

---

## 3. Layers

### `data/`

| File | Role |
|---|---|
| `MediaRepository.kt` | The interface abstracting all media access and file operations. |
| `MediaStoreDataSource.kt` | The `MediaStore`/`ContentResolver` implementation, plus copy/move/delete/create. |
| `GalleryTransformations.kt` | All pure filter, sort, and group logic, including date headers and path conversion. `sortMedia` takes the index's date-taken projection as a parameter rather than reaching for it, which is what keeps it Android-free and plain-JVM testable. It also owns `DateTakenSortState` — indexed with a date, indexed without one, not indexed — which rule evaluation shares rather than naming the same three states twice. Indexed without a date is not rare: on both devices measured so far most of the library had no capture date — [albums.md](../design/albums.md) §8 decision 21 has the figures and why no second date source is used — so a `time` rule can only ever match the dated minority there. |
| `GalleryModels.kt` | `SortType`, `SortOrder`, `GroupByType`, `DisplayMode`, `ViewType`, `OperationType`, `GalleryUiState`, `MediaPermissionHandler`. |
| `MediaItem.kt`, `Folder.kt` | The two domain models. |
| `ViewPreferences.kt` | The per-view preference bundle and `PreferenceScope`. |
| `FolderViewOverrides.kt` | The sparse per-folder override bundle and its apply/merge logic, plus the pure three-layer Folder Detail resolution shared by Folder Detail View and the New album screen, and the pure two-layer Album Detail resolution with its keys — `albumOverrideKey(id)` and the reserved `FAVOURITES_OVERRIDE_KEY`. The bundle and `PreferenceApplyTarget` serve albums too, under their folder names ([ADR-0021](decisions/0021-per-album-display-preference-overrides.md) point 9). See §5. |
| `CameraFolder.kt` | The camera-folder path heuristic and the implicit sort default it produces. |
| `MediaLocation.kt` | GPS parsing, formatting, URI building, and the per-item `MediaLocationReader`. Its `originalMediaUriForExifRead` — ADR-0001's `setRequireOriginal` path — plus `mediaCoordinatesFromExifLatLong` and the `isAtLeastQ` guard are shared with the Tier A indexer rather than reimplemented there. |
| `DefaultMediaPermissionHandler.kt` | Wraps the Android 11+ `createDeleteRequest`/`createWriteRequest` `IntentSender` APIs so the ViewModel stays free of `Activity` details. |
| `MediaStoreVersionStore.kt` | The `MediaStore.getVersion()` watermark store (`SharedPreferences*` and `InMemory*`) and the `MediaStoreVersionProvider` that reads the live value. |
| `EasyGalleryDatabase.kt` | The album Room database: two entities, schema version 1, `exportSchema = true`. Built by `create(context)`; the singleton lives on `EasyGalleryApp`. |
| `AlbumEntities.kt` | `AlbumEntity`, `AlbumMembershipEntity`, `AlbumMembershipKind`, and its type converter. |
| `AlbumDao.kt` | The album DAO. UI-facing queries return `Flow`. |
| `AlbumStore.kt` | `AlbumStore` plus `RoomAlbumStore` and `InMemoryAlbumStore`, and the `StoredAlbum` model. Besides ADR-0015's two automatic cover writes — first member added, cover's item removed — it carries the user's pick ([ADR-0015](decisions/0015-album-cover-persisted-random-member.md) Amendment 1): `setCover` points `coverMembershipId` at an addition row of that album and otherwise writes nothing, which is the write-path guard the schema cannot express; `setCoverToMedia`, for a rule match, finds or inserts the album's addition row for a `MediaIdentity` and points the cover at it in one transaction. |
| `AlbumMembership.kt` | Pure, Android-free membership resolution and healing — direct URI match, then healing key, then orphan — through `LiveMediaLookup` in `MediaIdentity.kt`. It returns resolved additions and exclusions separately, which is what rule evaluation composes with. |
| `AlbumRule.kt` | The rule model of **[ADR-0017](decisions/0017-composable-album-rule-representation.md)**: `AlbumRule.Evaluatable`, a non-empty conjunction of clauses; `AlbumRule.Unevaluatable`, which keeps the stored text verbatim with an `UnevaluatableReason`; the `time` clause as inclusive calendar-date bounds (Amendment 1); and the `region` clause of **Amendment 2** — a country, optionally an admin1 code with its name, optionally a place name, stamped with the gazetteer `dataset` it was written against. A `region` value can be built only for the current `PLACE_RESOLVER_VERSION`. The `people` clause of **Amendment 4** (step 3.5) is `People(anchor, box, pipelineVersion)` — the anchor photo's `MediaIdentity`, the face's `FaceBox` and the pipeline version it was found under, ADR-0022 §1's fields — built from the picker's `FaceGroupRepresentative` by `People.anchoredAt`. `People` and `Unevaluatable` redact `toString`, so neither a URI, a name nor the stored text reaches a log. A `NULL` column is a manual album and has no `AlbumRule` at all. |
| `AlbumRuleJson.kt` | The strict, hand-written reader and writer for `albums.rule`. Anything it does not fully understand becomes `Unevaluatable` and `parse` never throws — a `region` clause stamped with another dataset is `UNKNOWN_DATASET`; `encode` takes `Evaluatable` only, so an unevaluatable rule cannot be re-encoded, and escapes every string it writes so the stored rule is plain ASCII. A `people` clause is read as strictly — a bad anchor, pipeline version or box is `INVALID_ANCHOR`, `INVALID_PIPELINE_VERSION` or `INVALID_FACE_BOX` — and its box edges are written with `Float.toString`, which is locale-free and round-trips exactly; a negative zero reads as `+0f`. The envelope version stays 1. `encode` refuses a document over the 16 KiB cap by throwing, which is why §11 has a people-count entry. Hand-written because `org.json` is stubbed on the plain JVM and no serialisation dependency is approved. |
| `AlbumRuleSummary.kt` | `summarizeAlbumRule` — pure, read-only summary of an album's stored rule for the Album info dialog: `Manual` for no rule; `Unreadable` for an unevaluatable one, carrying neither its text nor its reason so the stored text cannot reach the screen; or `Automatic`, a list of place and date conditions, places in stored order and dates newest period first, each `time` clause named as a year, month, day or range, then — since step 3.6 — one `People(count)` for a rule with `people` clauses: a count, never an anchor. It holds no app string, and the caller supplies country names. It also owns `placeNames` and `placeChoiceLabel`, which the New automatic album screen's choice chips share. |
| `AlbumRuleEvaluation.kt` | `AlbumRuleEvaluator` — pure evaluation of `(rule matches ∪ additions) − exclusions` over a `MediaIndexLiveJoin`, in a zone the caller passes in, writing nothing back — and `AlbumEvaluationStatus`: `MANUAL`, `COMPLETE`, `WAITING_ON_INDEX` and `UNEVALUATABLE`. Each clause answers per item whether it matches and whether that answer is **settled**, ADR-0017 Amendment 2's rule: a pending row matches provisionally on its old place values, and an item makes the album wait only when some clause is unsettled and none is a settled no-match. An overload takes memberships already resolved, so `AlbumsViewModel` resolves each album once. An `UNEVALUATABLE` result carries an `AlbumUnevaluatableCause` — `RULE_NOT_UNDERSTOOD`, `PEOPLE_UNAVAILABLE_FACE_DATA_OFF`, `PEOPLE_UNAVAILABLE_DEVICE_INCAPABLE` or `ANCHOR_NOT_FOUND` — and no other result does: a cause, not a sibling status (ADR-0017 Amendment 4, step 3.5). Both overloads take a required, nullable `people: PeopleEvaluationInput?`; a rule with no `people` clause never reads it, and null with one is *not loaded yet*, which waits. Unavailability is told first, even before live media arrives. |
| `PeopleClauseEvaluation.kt` | Pure, Android-free `people` clause evaluation of **[ADR-0022](decisions/0022-people-criterion-anchored-to-a-photo.md)** §3 (step 3.5): `PeopleAvailability`, `PeopleEvaluationInput(availability, faces)`, the anchor resolution — the anchor found in live media through the join's lookup, its faces at the current pipeline version, the best IoU at or above 0.5 inclusive, computed in `Double` from the stored `Float`s, an exact tie to the lower face id, and a group only at the current grouping version, else waiting — and the per-item answer: a member is an image with any face in the group. Tier C data is trusted only while the live item, the winning index row and the face snapshot agree on the healing triple. *Not found* is the photo gone, not an image, `FAILED`, or no face at the box; it does not wait for grouping once the image's face state is current. |
| `PeopleAvailability.kt` | `peopleAvailabilityOf(faceDataEnabled, capability, currentKey)` — face data off first, then a capability record for the current key that says incapable; no record, another key's, or an unfinished check is unknown, and **unknown counts as available** (§11). `PeopleAvailabilitySource` reads the two persisted facts and never loads the pipeline: `StoredPeopleAvailabilitySource` over `FaceDataSettingStore` and `FaceDeviceStateStore`, and `InMemoryPeopleAvailabilitySource` for tests. The disclosure is not an input. The People picker asks a stricter question, `isPeoplePickerOffered` in `AutomaticAlbumPickerState.kt`, where unknown is *not* offered (step 3.6). |
| `MediaIdentity.kt` | The shared `MediaIdentity` / `MediaHealingKey` and their factories, and `LiveMediaLookup` — the one lookup of a stored identity against live media, direct URI then healing key, with the URI key injectable: raw for album memberships, canonical for the index join. One implementation of ADR-0010's identity, used by album healing and by the media index alike — **[ADR-0020](decisions/0020-automatic-albums-resolve-against-live-media.md)** forbids a second. Membership resolution, rule evaluation and index reconciliation all use the lookup — reconciliation with the canonical key (§11 has why). |
| `MediaIndexDatabase.kt` | The media index Room database: five entities, schema version 6, `exportSchema = true`, with `MIGRATION_1_2` to `MIGRATION_5_6` registered on the builder and no destructive fallback. `MIGRATION_2_3` changes data only; `MIGRATION_3_4` adds the resolver-version marker; `MIGRATION_4_5` creates the two Tier C tables, empty; `MIGRATION_5_6` creates the attempt table, empty — see §6. Built by `create(context)`, which also registers an open callback that turns `secure_delete` on. The file also holds what the face-data deletion uses (§6): the pragma helpers `enableSecureDelete`, `checkpointTruncate`, `journalMode` and `lockingMode`, and `deletionIsScrubbed`, the pure mapping from the journal and locking modes a deletion committed under to whether it may report a scrub — **[ADR-0018](decisions/0018-media-index-in-a-second-database.md)** Amendment 6's table. The singleton lives on `EasyGalleryApp`. |
| `MediaIndexEntities.kt` | `MediaIndexEntity` — the identity columns, the volume name, the generation columns, ADR-0018's captured and derived column split, and `derivedPlaceResolverVersion`, the marker that says which resolver wrote the derived columns — plus `MediaIndexWatermarkEntity`, the per-volume delta watermark keyed by volume name, and the two Tier C entities of **[ADR-0018](decisions/0018-media-index-in-a-second-database.md)** Amendment 5: `MediaIndexFaceStateEntity`, one per image the face pass has processed, and `MediaIndexFaceEntity`, one per stored face, with the outcome's type converter — and Amendment 7's `MediaIndexFaceAttemptEntity`, an image's decode attempts under one pipeline version, and `MediaIndexPendingFaceImageEntity`, the pending-images query's result. No Tier C entity has a foreign key, and all redact `toString`. |
| `MediaIndexDao.kt` | The index DAO. Observation queries return `Flow`, including `observeEvaluationProjection`, the narrow read that rule evaluation, the picker and `MediaIndexingViewModel`'s figures use — identity, captured date, whether coordinates exist, the three derived place columns and the resolver marker, as a query result rather than an entity, so it needed no schema change; deletion is per row, per volume, or whole-index. It carries the resolve pass's two queries — pending located rows paged by a URI cursor, and an update that writes the derived columns and the marker **only where the row still holds the coordinates the answer was computed from** — and the watermark queries: list, get, upsert, delete by volume. Its Tier C queries — read, insert, re-key and delete per image, delete per volume and in full, and whether any face data exists, each covering the attempt table too, plus `listPendingFaceImages`, the images-collection rows with no state at a pipeline version, paged by URI cursor with their attempt count — are building blocks the store composes inside its own transactions; none is a rule on its own. Grouping added four (step 3.4): faces at a pipeline version paged by id, whether any face there is pending grouping, the conditional update of one group's faces, and `listFaceGroups`, the picker query; the last two return query results, `MediaIndexFaceEmbeddingEntity` and `MediaIndexFaceGroupEntity`, declared in the DAO file, so the schema did not change. Step 3.5 added `observeFaceEvaluationRows`, one query joining the face state at a pipeline version to its index row and, left-joined, its faces — box and group, never the embedding — so one emission is one consistent snapshot; its result, `MediaIndexFaceEvaluationRowEntity`, is declared there too. |
| `MediaIndexStore.kt` | `MediaIndexStore` plus `RoomMediaIndexStore` and `InMemoryMediaIndexStore`, and the `MediaIndexEntry`, `MediaIndexEvaluationProjection` and `ResolvedPlaceWrite` models. `observeEvaluationProjection()` returns every row unfiltered, with stored rather than canonicalised URIs, so the join can heal a moved item through the shared lookup; a pending row keeps its old derived values beside a null or stale marker. It replaced the date-only identity projection of step 2.5, which was deleted with its model and the evaluator's date-only overload. `writeResolvedPlaces` applies a chunk of resolver answers in one transaction, conditionally, in both implementations alike. **It also owns Tier C** ([ADR-0018](decisions/0018-media-index-in-a-second-database.md) Amendment 5), with no store interface of its own, because every Tier C rule runs in the transaction that changes the image row: `writeFaceIndexResults` replaces an image's state and faces only while its row still has the identity they were computed from, and only while the face-data setting reads on inside its transaction, writing nothing and returning 0 otherwise; `recordFaceIndexAttempt` counts a decode attempt under the same two conditions, restarting at 1 under another pipeline version, and a result write or `clearFaceIndexAttempt` clears it (Amendment 7 §1); `listPendingFaceImages` pages the pass's work; `upsert` drops a written URI's Tier C rows in its transaction only when no row is stored there or the stored row's healing triple differs, so a re-extraction of an unchanged file — a metadata-only update, a volume resync — keeps them (Amendment 6); the three deletes clear the matching Tier C rows in theirs; `healMediaUri` moves a row in one transaction and re-keys its Tier C rows only when the healed row's triple equals the stored source row's, otherwise leaving neither the source's nor the target's. Both decide through one private test, `keepsTierC`, on `MediaIdentity`'s healing key. `deleteAllFaceData` empties both tables and reports whether the files were scrubbed (§6). `getFaceIndexState`, `listFaces` and `hasFaceData` read. `observeFaceEvaluationProjection(pipelineVersion)` (step 3.5) is the one Tier C `Flow`: a `FaceEvaluationSnapshot` that rule evaluation reads, kept apart from `observeEvaluationProjection` so a region or time album does not re-evaluate on a face write; the `InMemory*` store publishes a copy after each write that changes rows or Tier C. **Grouping's four** (step 3.4): `listFacesForGrouping` pages ids and embeddings at one pipeline version; `isFaceGroupingNeeded` is the *when grouping is needed* test of [the Stage 3 plan](../design/albums-stage-3.md); `writeFaceGroups` writes every group in one transaction, in chunks of 500 ids, only to faces still at the version, and reads the setting inside it; `listFaceGroups` is the People picker's read — §6 has its rules. The `InMemory*` store applies the same rules, copies embeddings in and out, and always reports a deletion scrubbed, having no file. |
| `MediaIndexTierC.kt` | Pure, Android-free Tier C models — `FaceIndexOutcome`, `FaceBox` in the upright frame **[ADR-0022](decisions/0022-people-criterion-anchored-to-a-photo.md)** §1 defines, `FaceEmbedding`, `FaceDetection`, `FaceIndexWrite`, `FaceIndexState`, `StoredFace`, `PendingFaceImage`, `FaceDataDeletion`, and the picker's `FaceGroupSummary` with its `FaceGroupRepresentative`, which carries exactly an anchor's fields under ADR-0022 §1 — the evaluation snapshot of step 3.5, `FaceEvaluationSnapshot` of `FaceEvaluationImage`s and `FaceEvaluationFace`s, looked up by stored URI, where an image with no face is present with an empty list — and the embedding codec, float32 little-endian. Every `toString` is redacted and no check message carries a value, which is the models' share of **[ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md)** §6's never-logged rule. |
| `FaceIndexPass.kt` | Pure, Android-free Tier C pass of **[ADR-0023](decisions/0023-face-pipeline-models-runtime-and-delivery.md)** §4: the `FacePipeline` interface and its exceptions, `FACE_PIPELINE_VERSION` and the constants it fingerprints, the attempt bound and the 7-minute budget, the box geometry into the upright frame, `FaceIndexLog` with `FaceIndexLogLines` — the fixed set of lines the face path may log, counts and fixed words only — `FaceIndexPass`, and `FaceDataStartupDeletion`. The pass pages pending images, records an attempt before each decode, writes once per image, writes `FAILED` without decoding after three attempts, and checks cancellation, the setting and the budget between images; the next run resumes from the index. The start-up deletion deletes face data whenever the setting reads off, whatever `hasFaceData()` says, and neither throws nor retries (ADR-0024 §3). `FaceIndexLogLines.groupingFinished` is grouping's one line, `face grouping finished stop=<NAME>`. |
| `FaceGrouping.kt` | Pure, Android-free grouping of [albums.md](../design/albums.md) §8 decisions 45 and 50: `groupFaces` — exact average linkage on cosine distance, cut at `FACE_GROUPING_CUT` (0.50) inclusive on a float32 triangle, run inside each connected component of the pairs at or below the cut (union-find over every pair, no distance kept), one triangle per component, freed before the next; the representative is the plain medoid, ties to the lower face id, and a group's id is the representative's face id. The result depends only on the set of (id, embedding). `FACE_GROUPING_VERSION` names the algorithm. A component larger than an int-indexed triangle can hold fails with a fixed message; the setting is probed between rows and merges, and a probe reading off returns null. Every model redacts `toString`. |
| `FaceGroupingPass.kt` | `FaceGroupingPass` and `FaceGroupingStop` (`GROUPED`, `NOT_NEEDED`, `SETTING_OFF`, `PAST_DEADLINE`): when grouping is needed it reads the faces in pages of 500, groups them, and writes once through `writeFaceGroups`; it starts only within 8 minutes of the worker's start, takes no lock of its own, writes nothing on any stop but `GROUPED`, and logs one line. Pure; `MediaIndexingWorker` is its only caller. |
| `FaceCapability.kt` | Pure, Android-free capability check of ADR-0023 §3: `FaceCapabilityKey` — app version code, pipeline version, build fingerprint — and `FaceCapabilityChecker`, which loads the pipeline and self-tests it on a synthetic input, persists the answer per key, records a start before it runs so two unfinished starts read as incapable, and does not persist an `OutOfMemoryError` or a build with no pipeline. Since step 3.6, `supportsFacePipelineAbi` (`FACE_PIPELINE_ABI`, arm64-v8a): a device that lists no such ABI is recorded incapable under the key at once, durably, and nothing is loaded. `FaceDataViewModel` makes the same test first, to skip `faceIndexLock` (§11). |
| `FaceDataSettingStore.kt` | The face-data setting of ADR-0024 §1 (`SharedPreferences*` and `InMemory*`): one boolean, on by default, in `app_settings`. `AlbumsViewModel` reads it through `StoredPeopleAvailabilitySource`; `FaceDataViewModel` writes it, from the Face data row and the disclosure's Turn off (step 3.6). |
| `FaceDeviceStateStore.kt` | `face_device_state` (`SharedPreferences*` and `InMemory*`): whether the face-data disclosure has been shown, and the capability record with its unfinished starts — state of this installation, excluded from every backup (§6). `AlbumsViewModel` reads the capability record through `StoredPeopleAvailabilitySource`; `FaceDataViewModel` runs the check at app open and marks the disclosure shown from its OK or Turn off, and from nothing else (step 3.6). |
| `OpenCvFacePipeline.kt` | The Android half of the pipeline: `decodeUprightForFaces` — `ImageDecoder`, which applies the EXIF orientation, software-backed, a long side of at most 1,280 px — and `OpenCvFacePipeline`, YuNet and SFace fp32 on OpenCV's DNN on the CPU, both models read from `assets/face/` into memory and never written to a file. It logs nothing, and no exception it throws carries a URI, a box or an embedding. |
| `MediaIndexWatermarkStore.kt` | `MediaIndexWatermarkStore` plus `RoomMediaIndexWatermarkStore` and `InMemoryMediaIndexWatermarkStore`, and the `MediaIndexVolumeWatermark` model — version, generation, and the API 28–29 date fallback, one entry per volume. |
| `MediaIndexDeltaDetection.kt` | Pure, Android-free delta detection: the API-branch resolution, the volume plan (which volumes resync and what the next watermarks are), the per-row selection, the `DATE_MODIFIED` fallback predicate, and the `MediaIndexDeltaDetector` that composes a snapshot provider with the watermark store. |
| `MediaIndexReconciliation.kt` | `MediaIndexReconciler` — deletes an index row whose media is gone, heals a moved one through the shared `LiveMediaLookup` keyed on `canonicalMediaStoreUriForJoin`, keeping the row's resolver marker, and returns without touching a volume that is not present. A heal is one call to the store's `healMediaUri`, so the row and its Tier C rows move in one transaction; it used to be a delete then an upsert. Unit-tested on a plain JVM with a mocked `Uri`, as `AlbumMembership` is; its one Android reference is an inlined `MediaStore` volume-name constant. |
| `MediaStoreVolumeSnapshotProvider.kt` | The `MediaStoreVolumeSnapshotProvider` interface, the `MediaStoreVolumeGateway` that isolates the four level-gated `MediaStore` calls, and the Android implementation that builds one snapshot per API branch. |
| `MediaStoreRowDeltaProvider.kt` | The `ContentResolver` query behind row selection: the per-volume image and video collections, the generation columns where they exist, and the legacy `external` collections on API 28. It canonicalises every observed URI at the source, so a row's spelling does not depend on which collection it was observed through, while the row keeps its own concrete volume name. |
| `AndroidMediaStoreVolumeVersionProvider.kt` | Reads a single volume's version string — the no-argument call for the primary volume, the per-volume call on API 29+. |
| `MediaIndexTierAIndexer.kt` | Tier A extraction and the batch that applies it: the `MediaIndexTierAExtractor` interface, its Android implementation (`ExifInterface` for images, `MediaMetadataRetriever` for video), the two pure date-taken parsers, and `MediaIndexTierABatchIndexer`, which composes detection, row selection, extraction, upsert, reconciliation and watermark persistence into one cancellable pass. The video parser treats a container's zero creation date as absent, and its `QUICKTIME_ZERO_EPOCH_MILLIS` constant is shared with `MediaIndexDatabase.MIGRATION_2_3`. A re-extracted row gets a null resolver marker and keeps its old derived values as labels until the resolve pass revisits it. `MediaIndexingWorker` is its only caller. |
| `Gazetteer.kt` | The pure nearest-place lookup of **[ADR-0019](decisions/0019-offline-reverse-geocoding-for-region-albums.md)** Amendment 1: parses the three gazetteer assets into primitive arrays, rejecting an empty, truncated, mis-versioned or malformed one with `GazetteerLoadException`; answers `nearest` on a 1° bucket grid with haversine and the global distance cap; and owns `PLACE_RESOLVER_VERSION`, which the asset headers must equal. It also holds `GazetteerNames`, a names-only loader for the admin1 and country assets with the same rejection rules, which never reads the places asset: the picker needs names, not coordinates. `countryDisplayName` asks the platform for the name in a locale and falls back to the asset's English name; region and place names stay in GeoNames' `name` form. |
| `GazetteerAssets.kt` | The Android side of the gazetteer: opens the packaged assets and hands them to `Gazetteer.load`, or, through `loadNames`, the two name assets to `GazetteerNames.load`. |
| `MediaIndexPlaceResolution.kt` | `MediaIndexPlaceResolver` — the resolve pass. Pages pending located rows by URI cursor, loads the gazetteer once and only if a row is pending, and writes each chunk's answers through the store's conditional write; a load failure propagates before anything is written. It reads only stored coordinates and emits one log line per pass carrying ADR-0019's M2 and M4 figures. `MediaIndexingWorker` is its only caller. |
| `MediaIndexLiveJoin.kt` | The evaluation projection joined to live media, one `JoinedMedia` per live item: its `DateTakenSortState` and a `LocationState` — resolved with a place, resolved beyond the cap, no coordinates, pending with old values, pending without values, or not indexed. Both sides join through `canonicalMediaStoreUriForJoin` and `LiveMediaLookup`, a direct match beating a heal. Each `JoinedMedia` also carries `indexedIdentity`, the stored URI and healing triple of the row that won it — the key of its Tier C data — and the join exposes `lookup` and `joinedItemOf`, which the `people` clause uses to find its anchor (step 3.5). `locationStateOf`, which classifies one projection row, is public because `MediaIndexingViewModel` classifies rows with it for Settings' location figure, so that figure and a `region` clause read a row the same way. The join counts no coverage: that computation left it when the figures moved to Settings, so the join carries nothing only Settings reads ([albums.md](../design/albums.md) §8 decision 29). |
| `AutomaticAlbumFacets.kt` | The picker's lists — countries, a country's regions and its places without a region, a region's places, years, a year's months — and the match count, each counted over a `MediaIndexLiveJoin` and filtered by the other dimension's choice. **A count is what `AlbumRuleEvaluator` would make members for the clause the entry writes**, with no additions or exclusions; a pending row counts under its old values, as the evaluator matches it. An entry that could not form a valid clause — a malformed country code, an empty code or name, a region the names asset cannot name — is dropped rather than listed. Since step 3.6 the place and date counts also follow the chosen people: `peopleMemberUris` evaluates the people clauses alone through `AlbumRuleEvaluator` and the facets intersect with that set, so the count still equals the album's. |
| `MediaLocationAccess.kt` | `MediaLocationAccessChecker` and its Android implementation: whether `ACCESS_MEDIA_LOCATION` is held, always true below API 29. It reads the permission state and requests nothing. Its one reader is `MediaIndexingViewModel`, so that Settings' location row says location access is off instead of counting — without the permission extraction stores no location, and a count would blame the photos. No album and no picker reads it ([albums.md](../design/albums.md) §8 decision 29; [ADR-0019](decisions/0019-offline-reverse-geocoding-for-region-albums.md) Amendment 3). |
| `MediaIndexingWork.kt` | The WorkManager boundary: `MediaIndexingWorker` (a `CoroutineWorker` that runs one Tier A batch, reports progress, and then runs the place resolve pass on every run, even one where Tier A selected nothing; a resolver failure fails the run; its `@JvmOverloads` constructor takes the run-state store, factories for the batch indexer and the place resolver, and the face path's seams, defaulting to `EasyGalleryApp`'s wiring, so tests can inject fakes while WorkManager still uses the two-argument form), and `MediaIndexingWorkScheduler` with its `WorkManager*` implementation — the periodic request and its constraints under the unique work name `media-index-periodic`, and the one-time "Index now" request under its own, `media-index-now`. **The face path** is in `doWork`'s order: after `markStarted`, `FaceDataStartupDeletion` runs before the permission check, because a deletion reads no media; after Tier A and places, the Tier C pass runs only if the setting reads on, the Tier C lock on `EasyGalleryApp` is free — taken before the capability check, so the periodic job and Index now never check at once — the device is capable, and the disclosure has been shown. **Grouping** (step 3.4) runs next in the same hold of the lock and behind the same gates, through the `createFaceGroupingPass` seam, unless the face pass stopped `SETTING_OFF`. Any throwable from either fails the run with the one fixed message, and the run state's counters stay Tier A's alone. `AndroidFaceIndexLog` writes `FaceIndexLogLines` under the tag `FaceIndexPass`. The worker runs in the app's process and writes through the shared index singleton, which is what lets evaluation follow a batch (§2). It **refuses to index without media read permission** and fails rather than running, because a run that cannot read rows would otherwise advance the watermark past the whole library — step 2.4's record in [the Stage 2 plan](../design/albums-stage-2.md) holds the detail. |
| `MediaIndexRunStateStore.kt` | The index run-state store (`SharedPreferences*` and `InMemory*`) — last outcome, timestamps and row counters for the Settings surface. Liveness is deliberately **not** among them; see *Persistence*. |
| `MediaIndexingLivenessStore.kt` | `MediaIndexingLivenessStore`, whose `WorkManager*` implementation derives a three-state `MediaIndexingWorkStatus` from `WorkInfo` across both unique work names rather than from anything persisted, and an `InMemory*` implementation for tests. `RUNNING` means a pass is executing; `WAITING` means WorkManager has work enqueued that its constraints keep from starting; `IDLE` means neither. Only `RUNNING` disables the Index now action. |
| `MediaStoreUriAliases.kt` | The single rule for `MediaStore` URI spellings: parse one into volume, kind and id, and canonicalise any volume into the `content://media/external/…` union namespace, so observed rows and live media join on one spelling. |
| `AlbumNaming.kt` | Pure default naming for a new album — the first `My album N` that no existing album uses. |
| `Favourites.kt` | Pure favourite-tier selection from SDK level, `MANAGE_MEDIA` grant, and R extension version. |
| `FavouritesDataSource.kt` | The `MediaStore` favourite read/write implementation behind the `FavouritesDataSource` interface. |
| `DisplayPreferencesStore.kt`, `FolderPreferencesStore.kt`, `FolderViewPreferencesStore.kt`, `FolderThumbnailStore.kt` | Persistence; see *Persistence*. |

There is **no `domain/` module**. Pure, Android-free logic lives in `data/`
(`GalleryTransformations`, `MediaLocation`, `CameraFolder`, `FolderViewOverrides`,
`AlbumMembership`, `MediaIdentity`, `AlbumRule`, `AlbumRuleJson`, `AlbumRuleEvaluation`,
`AlbumRuleSummary`, `MediaIndexLiveJoin`, `PeopleClauseEvaluation`, `PeopleAvailability`, `AutomaticAlbumFacets`, `MediaIndexDeltaDetection`,
`MediaIndexReconciliation`, `Gazetteer`, `MediaIndexPlaceResolution`, `MediaIndexTierC`,
`FaceIndexPass`, `FaceGrouping`, `FaceGroupingPass`, `FaceCapability`, `AlbumNaming`, `Favourites`), in `ui/AlbumListFiltering.kt`, and in `ui/components/` helper files
(`MediaDuration`, `ShareIntents`, `WallpaperIntents`, `MainViewBarRules`, `FaceCrop`, `AlbumStatusNotices`) specifically so it can
be unit tested on a plain JVM without Robolectric.
Operating on a `MediaItem` does not disqualify a file: its `uri` is mocked in those tests, which is
the `AlbumMembership` precedent `MediaIndexReconciliation` follows.

`MediaIndexTierAIndexer.kt` is deliberately **not** on that list. Unlike the delta-detection files,
which split the Android boundary into providers of their own, it keeps the extractor, the pure date
parsers and the batch orchestrator in one file — so its parsers are unit-tested plain while its
extractor is tested under Robolectric, and the file as a whole cannot be claimed as Android-free.

### `ui/`

- **`GalleryViewModel`** (`AndroidViewModel`) — the central state hub. It loads data,
  owns selection and file operations, and exposes the derived flows. It is the largest file in the
  app; prefer a new state holder or a feature ViewModel over adding to its body.
  - `selectMainView(mode)` is its half of a Main view bar tab tap: it exits folder and media
    selection, leaves Folder Detail View by `backToFolders()`, closes search in all five scopes
    through `DisplayPreferencesState.closeAllSearches()`, clears Show excluded, sets the
    `DisplayMode`, and clears `isSettingsMode`, because a tab tap can arrive from the Settings
    screen. `selectSettings()` is its half of a tap on the bar's Settings item: the same
    cancellations, then the `DisplayMode` set to Folders and `isSettingsMode` set, so that Back from
    Settings stays the single `setSettingsMode(false)` and lands on Folders View. The other halves
    are in `AlbumsViewModel` and `MainActivity` (§4).
  - `allMedia` starts as an `emptyList()` placeholder, so `isMediaLoaded` says whether it is real:
    false until `loadFolders()` has assigned the first result of `getAllMedia()`, never reset by a
    later reload, and still false if that first load throws. **Anything that must not read the
    placeholder as an empty library gates on the flag, not on emptiness** — a device with no media
    still delivers an empty list once loading finishes.
- **`DisplayPreferencesState`** — one `ViewPreferences` bundle per `PreferenceScope`, plus
  per-scope search state and the global `DisplayMode`.
- **`FolderViewPreferencesState`** — a sparse override map keyed by an opaque string, over one
  `FolderViewPreferencesStore`. `GalleryViewModel`'s instance holds the per-folder overrides, keyed
  by folder path; `AlbumsViewModel` holds a second, for Album Detail View (below).
- **`MediaViewerState`** — transient full-screen viewer state: current item, paging list,
  immersive mode, rotation, and the saved video position.
- **`MediaIndexDateTakenState`** — the index's date-taken projection, keyed by canonical
  `MediaStore` URI, as a `StateFlow` for `GalleryViewModel` to hand to `GalleryTransformations`.
  Its one job is to keep the Room `Flow` out of the ViewModel body.
- **`AlbumsViewModel`** — albums and their members. It observes `AlbumStore`, resolves stored
  memberships against the live media list it is handed, writes healed URIs and orphan flags back,
  and owns `selectedAlbumId` — the open Album Detail View — and `albumSelectionId`, the Album
  long-pressed in Albums View's Album selection mode. The second was a `remember` inside
  `FolderListContent` until the Main view bar, which must cancel it from `MainActivity`; a tap on
  any bar item, Settings included, clears both through `cancelAlbumSelectionAndDetail()`. It also owns the
  `MediaStore.getVersion()` watermark that decides
  when a re-resolve is worth doing. It takes a `MediaIndexStore` and evaluates each album's stored
  rule through `AlbumRuleEvaluator`, publishing an `AlbumEvaluationStatus` per album:
  - **One join, shared.** It combines the index's `observeEvaluationProjection()` with the live
    media list into one `MediaIndexLiveJoin` per emission of either, and every rule album and the
    picker read that instance; a unit test counts the builds. The join is shared with
    `WhileSubscribed(5_000)` and no replay after it stops, so it is collected only while a rule
    album exists or the picker is open — a manual album never observes the index — and the album
    list re-emitting on a rename does not restart the Room query.
  - Evaluation re-runs when the albums, an album's memberships, or the live media list change, and
    on every emission of the projection — which is how it follows each index batch (§2). Each album
    resolves its memberships once and hands that resolution to the evaluator.
  - **A second shared flow, for people albums** (step 3.5). `peopleInput` combines the index's
    `observeFaceEvaluationProjection(FACE_PIPELINE_VERSION)` with an availability epoch into a
    `PeopleEvaluationInput`, reading the availability from the injected `PeopleAvailabilitySource`
    on every emission rather than caching it. It is shared the same way as the join and combined
    only into the flow of an album whose rule has a `people` clause, so no other album pays for a
    face write. `refreshPeopleAvailability()` bumps the epoch, because switching face data on, or a
    capability check finishing, writes no Tier C row; since step 3.6 `MainActivity` calls it on every
    move of `FaceDataViewModel.peopleAvailabilityEpoch` — after the app-open check, on resume, and
    after either switch direction. No state holder was added. The evaluation's
    `AlbumUnevaluatableCause` is carried through `ComputedAlbum` to `AlbumListItem` and Album Detail
    View's state, and the pure `albumStatusNoticeKind(status, cause)` in
    `ui/components/AlbumStatusNotices.kt` maps it to one **Status notice** — the Newer-app notice for
    `RULE_NOT_UNDERSTOOD` or no cause, else the Face-data-off, Device-incapable or Person-not-found
    notice (step 3.6).
  - It runs on an injected `CoroutineDispatcher` — `Dispatchers.Default` in production, so off the
    main thread — and reads the zone through an injected provider at each evaluation rather than
    caching it, because the user can change the device zone while the app lives.
  - `AlbumListItem` carries the status beside the member counts — among them
    `mediaCountsByType`, the resolved members per `MediaType`, from which Albums View's media-type
    filter counts (§5) — and nothing about coverage or location access. Only `UNEVALUATABLE` is drawn, as one of the Status notices above; `WAITING_ON_INDEX` is
    still computed and published, but no tile, row or Album Detail View shows it
    ([albums.md](../design/albums.md) §8 decision 29;
    [ADR-0017](decisions/0017-composable-album-rule-representation.md) Amendment 3; §11).
  - `createAutomaticAlbum(name, rule)` encodes an `Evaluatable` rule and writes the album row with
    it and no membership rows — the first shipped path that writes `albums.rule` (§6). Since step
    3.6 it refuses a rule naming more than `MAX_PEOPLE_PER_ALBUM` people and catches the encoder's
    16 KiB `require`, so an over-long people rule is rejected rather than crashing (§11).
  - `selectedAlbumRule` is the open Album's stored rule as last read at evaluation — null for a
    manual album and while none is open. Its one reader is `AlbumDetailScreen`, which summarises it
    through `summarizeAlbumRule` for the **Album info dialog**. Country names come from
    `GazetteerAssets.loadNames` on `Dispatchers.IO`, and only for a rule with a place clause; a
    load failure falls back to the country code. Until the album is known — and, for such a rule,
    until that load has finished — `AlbumDetailContent` gets no summary and the menu has no Album
    info item; Favourites never has one.
  - **The Album cover picker**
    ([ADR-0015](decisions/0015-album-cover-persisted-random-member.md) Amendment 1), Album Detail
    View's draft-and-commit mode, ViewModel-owned under ADR-0002 and held here, not in
    `GalleryViewModel`:
    `isAlbumCoverPickerMode`, `albumCoverDraftUri` — only what was tapped this session — and
    `albumCoverPreselectedUri`, the saved cover when its membership resolves to a member and never a
    display-only fallback. `enterAlbumCoverPicker` does nothing with no Album open, so Favourites
    never enters it; `setAlbumCoverDraft` only drafts; `exitAlbumCoverPicker` writes nothing.
    `commitAlbumCoverDraft` writes only if the draft is still a member of the open Album, through
    `AlbumStore.setCover` when it has a membership row and `setCoverToMedia` when it is a rule match
    (§6). All three are cleared whenever the open Album changes or disappears, on its deletion, and
    by `cancelAlbumSelectionAndDetail()`. The screen exits `GalleryViewModel`'s media selection
    before entering the mode, and long-press does nothing while it is open.
  - **Per-album display overrides**
    ([ADR-0021](decisions/0021-per-album-display-preference-overrides.md)). It holds a second
    `FolderViewPreferencesState`, over `album_display_preferences` (§6), and publishes its map as
    `albumDisplayOverrides`. The five `commitAlbum*Preference` entry points take the album's key,
    the value, a `PreferenceApplyTarget` — `CURRENT_FOLDER` meaning this album, `ALL_FOLDERS` every
    album and Favourites — and a `writeGlobal` callback that the screen fills with the
    `GalleryViewModel` write to `ALBUM_DETAIL`. For the all-albums branch it runs that callback
    first and then clears the group from every key, so the order lives here, under a unit test,
    rather than in the screen. `setAlbumColumns` is the pinch-to-zoom path. `deleteAlbum` removes
    the album's key after its row; Favourites' key is never removed.
  - Removing an item from an album with **any stored rule** — evaluatable or not — **records an
    exclusion**, and deletes any addition row for the item as well. A rule match has no row to
    delete, an item removed while not yet indexed would otherwise reappear when its date is indexed,
    and a rule this build cannot read may be read by a newer one, which would re-match the item
    (ADR-0012). A manual album has nothing that can re-match, so removal deletes the row. One gap:
    `removeMembers` falls back to deleting the row for a membership id with no live match, because
    there is no `MediaItem` to build an exclusion from. It is unreachable in production because
    `removeMember` and `removeMembers` have no production caller; the only UI path is
    `removeMembersByMedia`, which has a `MediaItem` in hand.
  - `hiddenMemberCount` counts **stored additions that did not resolve**, and never rule matches:
    for a rule album, "stored minus resolved" would let a matched item mask a missing addition.
  - The live media list is null until the first one is handed in, and while it is null no healed
    URI or orphan flag is written back and a rule album reports `WAITING_ON_INDEX` — an empty list
    would mark every membership orphaned and every rule album complete. **Live-media forwarding is
    gated on `GalleryViewModel.isMediaLoaded`:** `MainActivity` and `FolderListScreen` call
    `setAllMedia` only once it is true, so the empty placeholder never arrives as a list.
- **`AutomaticAlbumPickerState`** — the New automatic album screen's draft and lists, a state
  holder **owned by `AlbumsViewModel`** and fed its shared join, so a picker count and the album
  created from it agree by construction. It holds the name, the chosen `region` and time clause,
  and which country, region and year each picker is drilled into; it loads `GazetteerNames` once,
  off the main thread, and reports loading, failed, waiting for media, or ready rather than an empty
  list. Its UI state is shared `WhileSubscribed(5_000)`, so facets are computed only while the
  screen is open. Zone and locale are read at each computation; `refresh()`
  recomputes them, which the screen calls on entry because a recreated Activity keeps the draft
  but not the locale its names were resolved in. `hasDraftName` tells the screen whether a draft
  was opened — false in a process restored onto the screen, which then opens one — because the
  UI state's initial value has an empty name either way.
  - **The People list** (step 3.6). The draft also holds the chosen people — `FaceGroupSummary`
    values, at most `MAX_PEOPLE_PER_ALBUM` (8), each written as `AlbumRuleClause.People.anchoredAt`
    its listed representative. The tab is offered by `isPeoplePickerOffered`, a fresh read of the
    stores on every availability refresh: face data on and the record for the current key capable,
    so unknown is not offered. When the offer goes, the draft's people are dropped. The list is the
    one query `MediaIndexStore.listFaceGroups`, loaded at the tab's first reveal in a visit, reloaded
    when indexing leaves `RUNNING` — but frozen while anyone is chosen, and reloaded on clear if a
    reload was skipped. The chosen people are read from the draft, never from the off-thread
    snapshot, so the chip shows no transient zero; `togglePerson` accepts only the exact listed
    summary. Only a draft with people chosen subscribes to `peopleInput`'s face snapshot, which
    keeps step 3.5's "only a people album subscribes" true. The tab is drawn by `ui/PeoplePicker.kt`.
- **`FavouritesViewModel`** — the favourite URI set and the write path, including the
  `IntentSender` round trip and the `MANAGE_MEDIA` rationale.
- **`CreateFolderViewModel`** — the create-folder dialog's own state; emits a `folderCreated`
  event the host screen listens for. Precedent for a feature-scoped ViewModel.
- **`BillingViewModel`** — the Google Play Billing client and the tip-jar purchase flow.
- **`MediaIndexingViewModel`** — the media index's state for two screens: the Settings screen's
  Media index area and the New automatic album screen's Index band. It is Activity-scoped —
  `MainActivity` holds the one instance and passes it to both — and it schedules the periodic
  job, starts "Index now", and combines run state, WorkManager's three-state liveness, the index's
  evaluation projection and whether location access is held into one UI state. The counts it
  publishes are **intersected with the live media list** it is handed, by canonical URI and without
  healing — so a moved item whose row has not yet healed counts as not yet indexed. It reports four
  separate figures rather than one percentage, because they are incommensurable: two about the
  indexing — indexed with a date, not yet indexed — and two **coverage figures** — indexed with no
  usable date, and indexed with no usable location, meaning no coordinates or resolved beyond the
  cap as the shared `locationStateOf` classifies a row. A pending or unindexed item is in neither
  coverage figure. Settings shows all four and is the only screen that measures progress; the
  coverage rows appear nowhere else and are kept apart from progress by their wording
  ([albums.md](../design/albums.md) §8 decision 29). Location access is read through
  `MediaLocationAccessChecker` at construction and again by `refreshLocationAccess()`, which the
  Settings screen calls on every `ON_RESUME`, entry included, because the permission changes outside
  the app; without it the location row shows the location-access-off sentence instead of a count.
  The picker reduces the same state
  through the pure `automaticAlbumIndexBand()` in `AutomaticAlbumPickerState.kt` to `Running` while
  liveness is `RUNNING`; otherwise to `NotIndexed`, carrying the not-yet-indexed figure, when that
  figure is above zero; otherwise to `Hidden`, which draws nothing. `NotIndexed` also carries
  whether nothing at all is indexed, which only `emptyListMayStillFill()` reads, to choose an empty
  list's wording. Liveness `WAITING` counts as idle there and falls to whichever of the last two the
  count gives. Settings does not fold it: the Media index area shows `WAITING` as a state of its
  own, with a detail line saying what the work waits for. Both keep Index now available while
  waiting, because only `RUNNING` disables it — the picker only while something is not yet
  indexed, since otherwise it has no band to carry it.
  Its live list is **not** gated on `isMediaLoaded`; §11 says what that costs.
- **`FaceDataViewModel`** (step 3.6) — face data's surfaces: the Face data area, the Face data
  disclosure and the Delete face data dialog's action, over the existing `FaceDataSettingStore`,
  `FaceDeviceStateStore` and `MediaIndexStore`; it adds no store and no preferences file
  ([ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md) Amendment 1). Its UI state
  is derived from the stores and never remembered, so a recreated Activity derives the disclosure
  again. **App-open work**, once per ViewModel on an injected IO dispatcher: read the stored
  capability record first and stop if it answers for the current key; on a device with no arm64 ABI
  record incapable without loading the models (`supportsFacePipelineAbi`); otherwise run the
  checker under `EasyGalleryApp.faceIndexLock`, which the worker takes before its own check; then,
  with the setting off, run the start-up deletion under the same lock. A `Throwable` there is
  caught, because an `Error` in a handler-less launch would end the process and the worker repeats
  the work. **Switch-off**, after the dialog's confirmation, in this order: persist off, take
  `faceIndexLock` so a running pass finishes, `deleteAllFaceData`, drop Coil's memory cache,
  release, re-read. The transient cleanup sentence is shown only when the deletion returned
  `scrubbed == false`, not when it threw, since a rolled-back transaction leaves nothing half-done
  and the start-up deletion repeats it. **Switch-on** persists on and nothing else: it never marks
  the disclosure shown. Every path above ends by moving `peopleAvailabilityEpoch`, which
  `MainActivity` forwards to `AlbumsViewModel.refreshPeopleAvailability()`. Activity-scoped like
  `MediaIndexingViewModel`: `MainActivity` holds it, hosts the disclosure, calls `onResume()`, and
  passes it to the Settings screen. Nothing in it logs, and no state names a photo or a person.

`AlbumsViewModel`, `FavouritesViewModel`, `MediaIndexingViewModel` and `FaceDataViewModel` are feature-scoped on that
precedent, and `AutomaticAlbumPickerState` is a state holder inside the first rather than a fourth
ViewModel, because its counts must read the albums' own join. The Index band is the converse case:
it is about the indexing job rather than the albums' join, so the screen reads
`MediaIndexingViewModel` directly and the picker state holds nothing of it. The Status area around
it keeps its state in the screen as well: whether it is expanded is one `rememberSaveable` flag in
`NewAutomaticAlbumContent`, shared by the three pickers and dropped with the screen's saved state when
the screen closes. The area holds the header, its collapse control and the Index band, and is
drawn only when `automaticAlbumIndexBand()` is not `Hidden`, because the band is its only content
([albums.md](../design/albums.md) §8 decision 29). The flag does not follow the area's content, so
an area collapsed, hidden and shown again comes back collapsed
([albums.md](../design/albums.md) §8 decision 27). `GalleryViewModel` gained no album
state and no indexing state; it still owns selection,
media operations, and the viewer delegate, which Album Detail View reuses. What it gained for the
index is one delegate, `MediaIndexDateTakenState`, because date-taken sorting is a property of the
flows it already owns.

Screens: `FolderListScreen` (with `FolderList`, `FolderGrid`, `FolderDialogs`),
`FolderDetailScreen`, `AlbumDetailScreen` (with `AlbumDialogs`), `NewAlbumScreen`,
`NewAutomaticAlbumScreen` (with `PeoplePicker`), `FullImageScreen`, `CalendarGrid`, `ManageExcludedScreen`, `SettingsScreen`.

Each main view is a pair: the `*Screen` composable reads the ViewModel and binds the scope; the
`*Content` composable is stateless and takes plain values plus callbacks. **The `*Content`
composable is what the instrumented tests drive, and they pass every parameter by name** — so a
signature change breaks them loudly, by design.

### `ui/components/`

Top bars (`SearchTopBar`, `SelectionTopBar`, `MediaSelectionTopBar`, and `ThumbnailPickerTopBar`,
shared by the Folder thumbnail picker and the Album cover picker, which passes its own title),
dialogs (`ColumnCountDialog`, `GroupByDialog`, `FilterMediaDialog`,
`CreateFolderDialog`, `MediaPropertiesDialog`, `FolderBrowser`, and the read-only
`AlbumInfoDialog`), the `SortOption` and `OrderOption` rows that `SortDialog` is built from, the
`PreferenceScopeSelector`
state holder those dialogs share for their draft-and-commit "apply only to this folder" or "this
album" target — its `PreferenceContainer` chooses the wording only, and both commit the same
`PreferenceApplyTarget` —
media widgets (`MediaGridItem`, `MediaListItem`, `AlbumGridItem`, `FavouriteIndicator`,
`ZoomableImage`), the **Main view
bar** (`MainViewBar`, with its `MainViewTab` enum), and the Android-free
helpers (`MediaDuration`, `ShareIntents`, `WallpaperIntents`, `MainViewBarRules`, `FaceCrop`, plus
`MediaLocationUi`, `VideoPlaybackState` and `DateLabels`, which are Compose-bound). `DateLabels`
is the one formatter for years and months, shared by the New automatic album screen and the Album
info dialog.

Step 3.6 added face data's components. `FaceDataDialogs.kt` holds the **Face data disclosure**
(`FaceDataDisclosureDialog`, a disclosure mode that neither Back nor an outside tap dismisses and an
info mode with one Close), the **Delete face data dialog** and the **Face licences dialog**, which
reads the five files under `assets/face/` off the main thread. `AlbumStatusNotices.kt` maps an
album's status and cause to its one Status notice (`AlbumStatusNoticeKind`), which the tile, the
row and Album Detail View all draw. `PersonFaceImage` draws a Person tile's face crop through a Coil
request built by `personFaceRequest`: a crop `Transformation` over `faceCropRect` — a square 1.8
times the box's larger side, shifted, never shrunk, to stay inside the image — with
`Precision.INEXACT`, no hardware bitmaps, and `diskCachePolicy` disabled, so a crop is never written
to disk; only the memory cache holds it, and switching face data off clears that
([ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md) Amendment 1 §4).

`MainViewBar(selectedTab: MainViewTab?, isSettingsSelected: Boolean, onTabSelected, onSettingsClick)`
is stateless: the caller derives which item is selected — a tab, or Settings, in which case
`selectedTab` is null — and decides what each tap does, a re-tap included. `MainViewTab` names the
three tabs only, because Settings is not a view. The bar draws its own fixed colours rather than
theme roles. `MainViewBarRules` holds the pure decisions `MainActivity` makes about it: which
screen the chain is showing, whether the bar shows there given the two picker modes, which tab is selected — none on the
Settings screen — and each tab's `DisplayMode` (§4).

`SearchTopBar` no longer carries a view switcher and takes no `DisplayMode`. Its `showAppIcon`
flag draws the app's logo before the title, which `FolderListContent` sets on the three main views;
the detail screens leave it off. An Overflow menu item whose callback is null is not drawn, which is
how each view gets its own menu: `FolderListContent` passes `onCreateFolderClick` in Folders View
only, `onCreateAlbumClick` in Albums View only, and `onShowExcludedClick` everywhere but Albums
View. No detail screen passes `onShowExcludedClick`: the flag is read only by the folder lists, so
from Folder Detail View it took effect only after going back. Both detail screens pass
`onChangeThumbnailClick`, with `changeThumbnailLabel` naming the folder or the album item;
`AlbumDetailContent` passes it only for an Album showing at least one item, never for Favourites.
Only `AlbumDetailContent` passes the last parameter, `onAlbumInfoClick`, whose item is the menu's
last.

### App entry points

- **`EasyGalleryApp`** (`Application`) — configures the Coil 3 `ImageLoader` with video-frame and
  animated-image decoders, applies the persisted locale in `attachBaseContext`, and holds both
  database singletons, `EasyGalleryDatabase` and `MediaIndexDatabase`, as `lazy` properties. There
  is no DI framework to own them and a second instance of either would defeat Room's invalidation
  tracking — **[ADR-0009](decisions/0009-room-as-first-owned-datastore.md)**. Two databases rather
  than one is **[ADR-0018](decisions/0018-media-index-in-a-second-database.md)**. It also builds the
  worker's collaborators over the index singleton — `createMediaIndexTierABatchIndexer()` and
  `createMediaIndexPlaceResolver()`, the latter loading the gazetteer through `GazetteerAssets` —
  and the face path's: the two face stores, the Tier C lock, `faceIndexLog`,
  `createFaceDataStartupDeletion()`, `createFaceCapabilityChecker()`, keyed by `faceCapabilityKey()`
  on the version code, `FACE_PIPELINE_VERSION` and `Build.FINGERPRINT`, `createFaceIndexPass()`,
  whose loader is `OpenCvFacePipeline.load`, and `createFaceGroupingPass()`. `faceCapabilityKey()`
  is the one place the key is built, so `createPeopleAvailabilitySource()` — `AlbumsViewModel`'s
  default availability source — reads the record under exactly the key the checker writes it.
- **`MainActivity`** — hosts the Compose tree, checks media permissions, and registers the
  `IntentSender` launchers for scoped-storage delete/write requests and for favourite writes. It
  also composes the Main view bar beneath the navigation chain (§4). Since step 3.6 it holds
  `FaceDataViewModel`, draws the Face data disclosure over the chain whenever
  `faceDataDisclosureDue` holds — media permission granted, no media open in the viewer, face data
  on, the device capable, the disclosure not yet shown — and forwards the ViewModel's availability
  epoch to `AlbumsViewModel`.
- **`LocaleHelper`** — persists the BCP-47 language tag and wraps a `Context` with it via
  `createConfigurationContext`. Its `supportedLanguageTags` is the list of shipped locales (§9).

---

## 4. Navigation

Navigation is a plain `if / else if` chain in `MainActivity` — **no Navigation library and no back
stack.** The order is:

```
selectedMedia != null        → FullImageScreen          (outside the state holder; no Main view bar)
  else, a Column of:
    Box(weight 1f), inside SaveableStateProvider(screenKey):
      isManageExcludedMode     → ManageExcludedScreen
      isSettingsMode           → SettingsScreen
      selectedFolder != null   → FolderDetailScreen
      selectedAlbumId != null  → AlbumDetailScreen
      isFavouritesSelected     → AlbumDetailScreen        (isFavouritesAlbum = true)
      newAlbumInitialName != null → NewAlbumScreen
      newAutomaticAlbumOpen    → NewAutomaticAlbumScreen
      hasPermission            → FolderListScreen
      else                     → PermissionDeniedScreen
    MainViewBar                (only when shouldShowMainViewBar is true)
```

Albums added two branches rather than a navigation library; the chain stays —
**[ADR-0013](decisions/0013-albums-as-third-display-mode.md)**. They sit after `selectedFolder`
and before `hasPermission`, so a folder still wins over an album and the permission gate still
wins over nothing. `selectedAlbumId` lives in `AlbumsViewModel`; `isFavouritesSelected` is a
`rememberSaveable` flag in `MainActivity`, because Favourites has no row of its own to select.

Album creation added the branch above `hasPermission` —
**[ADR-0016](decisions/0016-album-creation-type-choice-and-picker-screen.md)**.
`newAlbumInitialName` is a `rememberSaveable` nullable `String` in `MainActivity`: non-null means
the New album screen is open, and the value is the suggested name it opened on. It is reachable
only from Albums View, so its position relative to the detail branches is not load-bearing.

Automatic creation added the branch below it, at Stage 2 step 2.7 —
**[ADR-0016](decisions/0016-album-creation-type-choice-and-picker-screen.md)** Amendment 2.
`newAutomaticAlbumOpen` is a `rememberSaveable` `Boolean` in `MainActivity`, set by the Album type
dialog's Automatic option after `AlbumsViewModel.openAutomaticAlbumPicker()` has started a fresh
draft. Unlike the manual branch it carries no value, because the draft lives in
`AutomaticAlbumPickerState` rather than in the Activity. Reachable only from Albums View, so its
position is not load-bearing either.

### The Main view bar

The Main view bar is **not a branch** — it has no `screenKey` and adds none to the chain. It sits
below and outside the `SaveableStateProvider`, so it stays composed while the screen above it
changes, and the viewer branch renders neither the provider nor the bar —
**[ADR-0013](decisions/0013-albums-as-third-display-mode.md)** Amendment 2, point 4.

- **The chain's order is written three times.** The chain branches on the flags directly,
  `screenKey`'s `when` maps them to a key, and `resolveShownScreen` in
  `ui/components/MainViewBarRules.kt` maps the same flags, in the same order, to a `ShownScreen`.
  Reordering one without the others still compiles, so keep all three in step. `shouldShowMainViewBar` and
  `selectedMainViewTab` are derived from the `ShownScreen` — the first also from the two picker
  modes, `GalleryViewModel.isThumbnailPickerMode` and `AlbumsViewModel.isAlbumCoverPickerMode`,
  which hide the bar on a detail screen — and so is the Settings item's
  selection: `MainActivity` passes `shownScreen == SETTINGS`, and `selectedMainViewTab` returns
  null there. The selected item follows the screen rather than `displayMode` because the two can
  disagree — after process death `displayMode` resets to Folders while `isFavouritesSelected` is
  restored, and on Settings `displayMode` is Folders while no tab is selected.
- **The bar owns the bottom inset.** It pads itself for the system bars' and display cutout's
  bottom and horizontal insets, and while it shows, the `Box` above it consumes the bottom one so
  the screen's `Scaffold` does not pay it twice. Screens shown without the bar still apply it
  themselves.
- **A tap on the bar has three halves**, run together whatever item is tapped, the selected one
  included: `GalleryViewModel.selectMainView` for a tab or `GalleryViewModel.selectSettings` for
  Settings (§3), `AlbumsViewModel.cancelAlbumSelectionAndDetail()`, and clearing
  `isFavouritesSelected` here. It writes no history.
- **Settings opens over Folders View.** Because `selectSettings` sets `displayMode` to Folders as
  Settings opens, Back from Settings is the one `setSettingsMode(false)` call and lands on Folders
  View — never on the detail screen Settings was opened from, which the tap has already left. The
  Settings top bar carries its title and no back arrow, so system Back and the bar are the ways
  out. The Manage excluded screen, reachable only from Settings, keeps the bar hidden and its own
  back arrow; Back from it returns to Settings, with the bar, because `isSettingsMode` stays set
  beneath it and the chain tests `isManageExcludedMode` first —
  **[ADR-0013](decisions/0013-albums-as-third-display-mode.md)** Amendment 3.

Switching screens removes the previous one from composition, so a plain `remember` in a screen
does **not** survive navigation. A `rememberSaveableStateHolder` in `MainActivity` retains each
non-viewer screen's saveable state (lazy scroll position) under a `screenKey` that includes the
folder path or album id — `folder_detail:$path`, `album_detail:$albumId`, the literal
`album_detail:favourites`, `new_album`, and `new_automatic_album`. Settings keeps its scroll
position under `settings`, so leaving it by a tab and coming back restores it.

The three main views share one entry, `folder_list`. Inside it, `FolderListContent` nests a second
`rememberSaveableStateHolder` keyed by `DisplayMode.name`, so each main view keeps its own grid
and list scroll position across a tab switch, a detail screen, the viewer, and rotation. Before it,
Folders View and Albums View shared one grid and one list state, and Timeline View lost its
position whenever it left composition — ADR-0013 Amendment 2, point 8.

`FullImageScreen` is deliberately rendered **outside** that provider, and `MainActivity` carries
no `android:configChanges`. Both are required by
**[ADR-0002](decisions/0002-viewmodel-owned-transient-viewer-state.md)** — read it before touching
either. Albums left both untouched, and so did the Main view bar.

The `new_album` and `new_automatic_album` entries are the two keys `MainActivity` **removes** from
the holder, each in a `LaunchedEffect` once its screen closes. Retention is right for a scroll
position and wrong for a draft: without the removal the next New album screen would reopen on the
previous name — by then a duplicate — and the previous selection, and the next New automatic album
screen on the previous visit's tab and typed name.

State that must survive navigation therefore belongs either in a ViewModel or inside the
`SaveableStateProvider`.

---

## 5. Display preferences

`ViewPreferences` holds `sortType`, `sortOrder`, `viewType`, `columns`, `groupBy`, `groupOrder`,
`mediaTypes`, and `showInfo`. Each main view owns an independent set, keyed by `PreferenceScope`
(`FOLDERS`, `TIMELINE`, `FOLDER_DETAIL`, `ALBUMS`, `ALBUM_DETAIL`), so changing the grouping in
one view does not change another. `GalleryViewModel` exposes `preferences(scope)`,
`searchQuery(scope)`, and `isSearchActive(scope)` rather than one flow per field, and setters take
the scope: `setGroupBy(type, scope)`.

Derived flows in `GalleryViewModel` are bound to the owning scope: `filteredFolders` → `FOLDERS`,
`filteredAllMedia`/`groupedAllMedia` → `TIMELINE`, `filteredMedia`/`groupedFolderMedia` →
`FOLDER_DETAIL`. The two album scopes have **no ViewModel-level derived flow**: Albums View runs
the pure `filterAndSortAlbums` (`ui/AlbumListFiltering.kt`) under `remember` inside
`AlbumsViewBody`, and Album Detail View runs `GalleryTransformations` under `remember` inside
`AlbumDetailScreen`, because the input is an `AlbumsViewModel` list rather than a
`GalleryViewModel` flow.

**Albums View's media-type filter hides an album it empties, as Folders View's hides a folder, but
leaves covers and the Favourites tile alone.** `filterAndSortAlbums` keeps the albums the search
query matches, counts each over the selected types from `AlbumListItem.mediaCountsByType`, and
drops one whose filtered count is 0 only if its unfiltered count is not — an album that is empty
anyway stays listed so it can be opened and filled. Size sorts by the filtered count. Unlike
`filterAndSortFolders`, whose automatic thumbnail becomes a folder's first matching item, it leaves
the cover untouched. `AlbumsViewBody` shows the Favourites tile whenever search allows, with a
filtered count and an unfiltered cover. Its "No albums yet" message appears only when nothing is
left before the media filter; a list emptied by the filter alone is simply blank, as Folders View's
is. The filter is `ALBUMS`' own `mediaTypes` in `display_preferences`, so it needed no new store —
[albums.md](../design/albums.md) §8 decision 30, which records that this reverses an earlier
count-only behaviour and why.

`newAlbumFolders` is the exception to that scope binding: it reads the `FOLDERS` bundle for sort
order and layout, and for pinning, exclusion, and thumbnail overrides, but passes an empty search
query and every `MediaType` to the same `filterAndSortFolders` call that produces
`filteredFolders`. The New album screen's folder level is a picker, and a browse-state filter —
especially a persisted one — must not silently hide a folder the user came there to pick. See
**[ADR-0016](decisions/0016-album-creation-type-choice-and-picker-screen.md)**, Amendment 1 and
its Corrections.

| | Folders View | Timeline View | Albums View | Folder Detail View | Album Detail View |
|---|---|---|---|---|---|
| Sort | Name, ascending | Date taken, descending | Name, ascending | Name, ascending | Name, ascending |
| Group by | — | Date taken (daily) | — | None | None |
| Layout | Grid, 2 columns | Grid, 3 columns | Grid, 2 columns | Grid, 3 columns | Grid, 3 columns |

Albums View mirrors Folders View's defaults because it is structurally the same grid of
containers; Album Detail View mirrors Folder Detail View's for the same reason.

Search is scoped the same way but **deliberately not persisted** — restoring a stale query on
launch would silently hide media.

### Folder Detail resolves three layers

For Folder Detail View the effective preferences are composed, not read flat:

```
explicit per-folder override  ⟶ over ⟶  implicit camera default  ⟶ over ⟶  global FOLDER_DETAIL
```

- The **per-folder override** is sparse, keyed by `Folder.path`, and merged on read, so no screen
  read path changed when it was introduced —
  **[ADR-0006](decisions/0006-per-folder-display-preference-overrides.md)**.
- The **camera default** is computed from the folder path and **never persisted** —
  **[ADR-0008](decisions/0008-camera-folder-default-sort-order.md)**.

The nesting order matters and reversing it still compiles. Read both ADRs before changing this.

The composition itself is **pure, Android-free, and shared**, in `data/FolderViewOverrides.kt`: a
layer bundle carrying the global preferences, the override map, and whether the sort was set by
the user, plus a function that resolves it for **any** folder path. `GalleryViewModel` publishes
the bundle as a `StateFlow` and resolves its own selected folder through that function, which is
what `preferences(FOLDER_DETAIL)` returns. The **New album screen**'s media level calls the same
function for whichever folder it has open, so the picker shows a folder the way Folder Detail View
would — **[ADR-0016](decisions/0016-album-creation-type-choice-and-picker-screen.md)**,
Amendment 1. Resolve the layers anywhere else by hand and the three surfaces drift apart.

ADR-0006 also made the five display-preference dialogs **draft-and-commit**, with an "apply only
to this folder" target chosen at OK rather than on selection.

### Album Detail resolves two layers

Album Detail View — an Album or Favourites — composes its preferences the same way, with no middle
layer:

```
explicit per-album override  ⟶ over ⟶  global ALBUM_DETAIL
```

The override is the same sparse `FolderViewOverrides`, keyed by the album's id as a decimal string
or by the reserved `favourites`, in a preferences file of its own (§6) —
**[ADR-0021](decisions/0021-per-album-display-preference-overrides.md)**, which reuses ADR-0006's
model rather than copying it. There is no camera default, because that is a property of a folder
path. The same five dialogs say "apply only to this album", and pinch-to-zoom writes the album's
override.

Unlike Folder Detail View, the two layers come from two ViewModels: the global bundle from
`GalleryViewModel.preferences(ALBUM_DETAIL)`, the override map from `AlbumsViewModel` (§3).
`AlbumDetailScreen` composes them under `remember` through the pure
`resolveAlbumDetailPreferences(global, overrides)` in `data/FolderViewOverrides.kt`, in keeping
with the album scopes having no ViewModel-level derived flow. Until an open Album is known its key
is null — Favourites' never is: the global bundle is shown, a "this album" commit does nothing
while an "all albums" one still works, and pinch-to-zoom changes the global column count
(ADR-0021 Amendment 1).

---

## 6. Persistence

The app persists in `SharedPreferences` and in two Room databases, and they all sit **alongside**
each other. Room did not replace the preference stores: they work, they are tested, and migrating
them would be churn with no user-visible benefit —
**[ADR-0009](decisions/0009-room-as-first-owned-datastore.md)**.

### Preference stores

Each file has exactly one store, and no two files are merged — the per-folder override file parses
structured keys and would collide with flat ones. One store interface, `FolderViewPreferencesStore`,
backs two files, and they must stay two: its `clear(group)` removes a field from every key in its
file, which is how "apply to all" works, so folder and album overrides sharing a file would reset
each other — **[ADR-0021](decisions/0021-per-album-display-preference-overrides.md)**.

| File | Holds | Interface |
|---|---|---|
| `app_settings` | language override; the `MediaStore.getVersion()` watermark; the face-data setting, `face_data_enabled` | `LocaleHelper`, `MediaStoreVersionStore`, `FaceDataSettingStore` |
| `display_preferences` | per-view `ViewPreferences` | `DisplayPreferencesStore` |
| `folder_preferences` | pinned and excluded folder paths | `FolderPreferencesStore` |
| `folder_display_preferences` | per-folder preference overrides | `FolderViewPreferencesStore` |
| `album_display_preferences` | per-album and Favourites preference overrides | `FolderViewPreferencesStore`, owned by `AlbumsViewModel` |
| `folder_thumbnails` | per-folder thumbnail override | `FolderThumbnailStore` |
| `media_index_run_state` | the last indexing run's outcome, timestamps and row counters | `MediaIndexRunStateStore` |
| `face_device_state` | whether the face-data disclosure has been shown; the capability record and its unfinished starts | `FaceDeviceStateStore` |

Every store is an interface with a `SharedPreferences*` production implementation and an
`InMemory*` implementation for tests. Add both when you add a store.

`app_settings` is the one file with more than one owner. The album watermark is a single scalar under
`albums_mediastore_version`, written by `SharedPreferencesMediaStoreVersionStore` in
`data/MediaStoreVersionStore.kt` and injected into `AlbumsViewModel`; it was put there
deliberately rather than opening a preferences file for one key. A second scalar has joined
it: the face-data setting, `face_data_enabled`, which is the user's intent and so is backed up
(**[ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md)** §1). Anything structured must not — the media index's per-volume watermark was briefly written
here, is **not** a scalar, and lives in the index database instead (§6, *The media index
database*).

The folder thumbnail override is resolved against live media rather than trusted blindly, so a
deleted or moved item falls back automatically —
**[ADR-0007](decisions/0007-persisted-folder-thumbnail-override.md)**, including its two
amendments covering the draft-and-commit picker and pre-selection.

`media_index_run_state` was the first preferences file **excluded from backup**. It was
challenged on ADR-0018 Amendment 1's test — the test that moved the per-volume watermark *into* the
index database — and kept here; **[ADR-0018](decisions/0018-media-index-in-a-second-database.md)**
Amendment 2 records why that test does not reach it. Two consequences live here rather than in the
ADR, because they are structure:

- **It holds no liveness flag.** Whether indexing is running, merely waiting on its constraints, or
  idle is read from WorkManager through `MediaIndexingLivenessStore` (§3), never from this file. A
  persisted `is_running` does not survive
  process death correctly — a killed worker leaves it true forever, and the Index now action stays
  disabled with no way out short of clearing app data. `SharedPreferencesMediaIndexRunStateStore`
  actively removes that key on every write, which is the scar of having tried it.
- **It carries an `<exclude>`**, in `backup_rules.xml` and in all three sections
  of `data_extraction_rules.xml`, beside the index database's four. Restoring a row count and a
  "last indexed" timestamp from another device would describe an index that does not exist on this
  one.

`face_device_state` is the second, and is excluded in the same four places. It holds what belongs
to this installation on this device — whether the face-data disclosure was shown
(**[ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md)** §2) and the capability
record (**[ADR-0023](decisions/0023-face-pipeline-models-runtime-and-delivery.md)** §3) — so a
restore must not carry either to another phone. It is a file of its own rather than keys in
`app_settings` because `app_settings` is backed up —
**[ADR-0018](decisions/0018-media-index-in-a-second-database.md)** Amendment 7 §3. The capability
record counts a start before the check runs and is written durably, because the check may end
the process.

### The album database

`easy_gallery.db`, Room, schema version 1, exported to `app/schemas/`. Two tables:

| Table | Holds |
|---|---|
| `albums` | id, name, `createdAt`, a nullable `rule`, and a nullable `coverMembershipId` |
| `album_memberships` | id, `albumId`, `mediaUri`, the healing key (`displayName` + `size` + `dateModified`), `kind`, `isOrphaned` |

An album's contents are `(rule ∪ additions) − exclusions`, which is why memberships carry a
`kind` rather than being a plain join table —
**[ADR-0012](decisions/0012-one-album-type-rule-additions-exclusions.md)**. The `rule` column holds
the versioned JSON document of
**[ADR-0017](decisions/0017-composable-album-rule-representation.md)**, whose Amendment 1 fixes the
`time` clause, the zone it is read in, and how strict the reader is, and whose **Amendment 2** adds
the `region` clause, its dataset stamp and the settled/unsettled rule: `NULL` means manual, and a
rule this build cannot read resolves to additions minus exclusions and is never rewritten. Its
**Amendment 4** adds the `people` clause — an anchor photo's identity, a face box and a pipeline
version (ADR-0022) — which step 3.5 built into the same column with the envelope still at version 1
and no schema change; since step 3.6 the People picker writes one, through `People.anchoredAt`, at
most `MAX_PEOPLE_PER_ALBUM` per rule. It is
read by `AlbumsViewModel` and written by `AlbumsViewModel.createAutomaticAlbum`, from the New
automatic album screen, once, at creation — no shipped path rewrites a stored rule (§11). The
album schema did not change for it. A membership identifies
its media by a content URI with the healing key as a fallback, and an unresolvable membership is
marked orphaned and retained rather than deleted —
**[ADR-0010](decisions/0010-album-membership-dual-key-identity.md)**. The cover is a foreign key
to a membership row, chosen at random once, so it heals through the ordinary membership path —
**[ADR-0015](decisions/0015-album-cover-persisted-random-member.md)**. The user can replace it in
Album Detail View's Album cover picker; picking a rule match first writes an addition row for it, in
the same transaction, so the column still points only at the album's own rows and no schema or
preference file changed — its **Amendment 1**.

The database is in scope for Android auto-backup and device transfer, which is the project
template's default kept deliberately —
**[ADR-0014](decisions/0014-album-database-included-in-auto-backup.md)**: `allowBackup="true"`, and
the only elements in either rules file are `<exclude>` entries — four for the index database and
its sidecars, and one each for the `media_index_run_state` and `face_device_state` preferences
files. `BackupRulesParityTest` holds the four sections to that one set.
**There is no `<include>` anywhere, and that is what keeps the album database in scope** — adding
one would silently narrow the whole backup to it, so do not "tidy" those files.

**Favourites are not in this database.** They are a `MediaStore` column, read and written through
`FavouritesDataSource` with a tier chosen from the SDK level, the `MANAGE_MEDIA` grant, and the R
extension version — **[ADR-0011](decisions/0011-favourites-backed-by-mediastore.md)**.
`MANAGE_MEDIA` is declared in the manifest, requested only in context, and never required.

### The media index database

`media_index.db`, Room, schema version 6, exported to `app/schemas/` under its own directory. Five
tables:

| Table | Holds |
|---|---|
| `media_index` | one row per indexed item: the content URI, the same healing triple an album membership carries, the volume name, the generation numbers, the captured EXIF columns, the derived place columns, and the resolver-version marker |
| `media_index_watermark` | one row per volume: the last-seen version string, the last-seen generation, and the API 28–29 `DATE_MODIFIED` fallback watermark |
| `media_index_face_state` | Tier C: one row per image the face pass has processed — its URI, the face-pipeline version, and the outcome, indexed or failed |
| `media_index_face` | Tier C: one row per stored face — an id never reused, its image's URI, the face-pipeline version, the box as four fractions, the embedding as a float32 blob, and a group id and grouping version, both null until grouped |
| `media_index_face_attempt` | Tier C: one row per image whose decode was attempted and has no result yet — its URI, the face-pipeline version, and the attempt count |

EXIF facts captured from a file and the place codes later derived from them are **separate
columns**, and the derived ones are written only by the place resolve pass —
**[ADR-0018](decisions/0018-media-index-in-a-second-database.md)** owns both rules and the reason
this is a second database rather than two more tables in the first. Its Amendment 1 records why the
watermark table is here rather than in `SharedPreferences`.

The watermark table arrived through a real `Migration(1, 2)` registered on the builder, with **no
destructive fallback**. The precedent that sets for every later index schema change — bump,
migrate, commit the JSON — is
**[ADR-0018](decisions/0018-media-index-in-a-second-database.md)** Amendment 1's, and is not
restated here. The two databases version independently, so this moved neither the album schema nor
its history.

The second migration, `Migration(2, 3)`, changes **data, not schema**: it nulls a captured date
equal to a video container's zero creation date, which earlier builds stored as a real date and
which delta detection would never re-extract. The entity, and so the identity hash, did not change.
ADR-0018 **Amendment 3** records why a migration rather than a read-time filter or a resync.

The third, `Migration(3, 4)`, is a schema change: it adds `derivedPlaceResolverVersion`, with no
default, so every existing row reads as not yet resolved. The marker is what tells a row resolved
beyond the distance cap — null country, region and place — from one nobody has resolved, and a
bump of `PLACE_RESOLVER_VERSION` makes every older row pending again with no file I/O. ADR-0018
**Amendment 4** owns who writes it: Tier A writes it null, a reconciler heal keeps it, and the
resolver writes it with the derived columns only where the row's coordinates are unchanged, because
the periodic job and Index now can run at once.

The fourth, `Migration(4, 5)`, creates the two **Tier C** tables empty and changes no existing
table, so every image starts pending. Tier C is face data: what it holds, its two stamps and its
per-image states are **[ADR-0018](decisions/0018-media-index-in-a-second-database.md)**
**Amendment 5**'s. The tables carry no foreign key — the store's `INSERT OR REPLACE` upsert would
fire a cascade on every Tier A write — so `MediaIndexStore` applies each rule itself, in the
transaction that changes the image row (§3).

The fifth, `Migration(5, 6)`, creates `media_index_face_attempt` empty and changes nothing else —
**[ADR-0018](decisions/0018-media-index-in-a-second-database.md)** **Amendment 7** §1. The face
pass records an attempt before each decode and clears it with the image's result, so an image that
ends the process three times is written `FAILED` without a fourth decode; a result or an attempt is
written only while the image row keeps the identity it names and **only while the face-data setting
reads on inside the write's transaction**, so a pass that races the switch writes nothing after it.
The attempt rows follow every Tier C rule — dropped, re-keyed and deleted with the image's state,
emptied by `deleteAllFaceData` and counted by `hasFaceData`. The pass is wired into the worker
(§3, `MediaIndexingWork.kt`) and runs once the disclosure has been shown, which since step 3.6 of
[albums-stage-3.md](../design/albums-stage-3.md) the disclosure's OK or Turn off records.

**Grouping fills the two columns the fourth migration left null, with no sixth.** Step 3.4 writes a
face's `groupId` — the face id of its group's representative, the medoid — and `groupingVersion`,
`FACE_GROUPING_VERSION`, which says which algorithm wrote it; the index stays at schema version 6
(ADR-0018 Amendment 8, which would have added a column, was reversed before it was built). Grouping
is needed when some face at the current pipeline version has no grouping version or another one, or
a group id naming no face at that version — a representative deleted or dropped by a changed
healing triple; a deleted face that was not a representative does not count. It runs in the worker
after the face pass (§3, `MediaIndexingWork.kt`), regroups every face at the version in memory, and
writes every group in one transaction that reads the setting inside it, so a stop at any point
writes nothing and the next run repeats the work. `listFaceGroups` is the People picker's read: the
groups at both versions whose representative still exists, with at least a minimum count of
distinct images among the index rows that still exist, ordered by that count, then newest capture
date with none last, then group id ([albums.md](../design/albums.md) §8 decision 46). Since step
3.6 its one reader is `AutomaticAlbumPickerState`'s People list, with a minimum of
`PEOPLE_PICKER_MINIMUM_IMAGES` (2).

**Deleting face data zeroes the deleted bytes, and reports when a copy may remain.** `create` turns
`PRAGMA secure_delete` on when the database opens, so every Tier C delete and update zeroes the old
bytes, and a failed read-back there does not stop the index opening. `deleteAllFaceData` sets it
again as the first statement of its transaction and refuses to delete unless it reads back on, and
reads `journal_mode` and `locking_mode` on that same connection. After the commit,
`deletionIsScrubbed` decides what the result may report, by
**[ADR-0018](decisions/0018-media-index-in-a-second-database.md)** Amendment 6's table: under `wal`,
whether `PRAGMA wal_checkpoint(TRUNCATE)` emptied the log, which a reader still holding the old
snapshot prevents; under `delete` or `truncate`, scrubbed only with `normal` locking; under `memory`
or `off`, scrubbed; under `persist` or a mode it does not recognise, not scrubbed, because the
journal may keep the deleted pages' copies. Not scrubbed is a result, not an error: the rows are
deleted either way, and the caller repeats the deletion — which on a `PERSIST` device reports not
scrubbed again. There is no `VACUUM`. The mechanism and why are Amendment 5's, as step 3.2 annotated
it, and what may be reported is Amendment 6's; **when** a deletion runs — the Settings switch, and a
repeat at start-up — is
**[ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md)** §3's. The repeat is wired:
`FaceDataStartupDeletion` runs first in every worker run while the setting reads off. Since step
3.6 the switch and the repeat at app open are wired too, both in `FaceDataViewModel` (§3) under
`faceIndexLock`.
`MediaIndexFaceDataScrubTest` proves the result under Robolectric for `wal`, `truncate` and
`persist`, and `MediaIndexScrubModeTest` the mapping on a plain JVM; what only a device can show —
the phones' real modes among it — ~~is left to step 3.7 of the stage plan~~ was read at step 3.7:
both phones' indexes use `wal` with `normal` locking, and `MediaIndexSqliteDeviceTest`'s three
behaviours held on real SQLite on both ([albums-stage-3.md](../design/albums-stage-3.md) *3.7*, G4,
G9 and G10).

**The gazetteer the resolve pass reads is not a database.** It is three UTF-8 TSV assets under
`app/src/main/assets/gazetteer/` — places, admin1 names, country names — generated by hand from
GeoNames with `tools/gazetteer/build_gazetteer.py`, committed, and parsed into memory by a worker
run that finds a pending row; nothing writes it at runtime. Each asset's header records its sources,
their SHA-256, row counts and the resolver version it was stamped with —
**[ADR-0019](decisions/0019-offline-reverse-geocoding-for-region-albums.md)** Amendment 1. The data
is GeoNames' under **CC BY 4.0**, and its attribution is the Settings screen's **Place data row**
in the Legal section, which must survive any reorganisation of Settings.

It is **excluded** from backup and device transfer — the file and its `-wal`, `-shm` and `-journal`
sidecars, in `backup_rules.xml` and in all three sections of `data_extraction_rules.xml`:
`<cloud-backup>`, `<device-transfer>`, and `<cross-platform-transfer platform="ios">`, the fourth
section, which arrived with step 3.3 because Android documents a transfer mode with no rules as
fully enabled, and whether this app is ever transferred to iOS is not established — ADR-0018
Amendment 7 §2, whose residual F1 is still open. The
`-journal` exclusion arrived with Tier C: Room uses a rollback journal on a low-RAM device, and one
left hot by a crash holds pages as they were before the transaction. Tier C is in the same file, so
face data is out of cloud backup and device transfer by these rules, which is how
**[ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md)** §6 says it is checked. The
`media_index_run_state` and `face_device_state` preferences files are excluded in the same four
places. Backup rules select files rather than tables, which is why the split into two databases is
what makes "back up albums, never back up the index" expressible at all. The exclusion costs
nothing on restore because every row is recomputable from the device — the watermark rows
included, which is the argument for keeping them here rather than beside the album watermark in
`app_settings`.

The index is **a cache, not a record of intent**: a row whose media is gone is deleted rather than
retained, which is the opposite of what an album membership does, and an automatic album's contents
are the index's keys intersected with live media rather than the index's rows —
**[ADR-0020](decisions/0020-automatic-albums-resolve-against-live-media.md)**. Do not "fix" the
apparent inconsistency with ADR-0010 in either direction; the two differ because a membership is a
user's choice and an index row is not.

It is filled by `MediaIndexingWorker` under WorkManager and read by `MediaIndexDateTakenState` for
`SortType.DATE_TAKEN`, by `MediaIndexingViewModel` for the Settings figures and the New automatic
album screen's Index band, and by
`AlbumsViewModel` for automatic-album evaluation and the picker's counts — see §2. Evaluation
implements three clauses, `time`, `region` and `people`; the `region` clause reads the derived place
columns and the resolver marker through the evaluation projection, and a row whose marker is not the
current `PLACE_RESOLVER_VERSION` is pending — ADR-0017 Amendment 2. The `people` clause (step 3.5)
reads Tier C through its own `Flow`, `observeFaceEvaluationProjection`, never an embedding, and only
for an album that has one; whether it can be evaluated at all is read from the two preference
stores — the face-data setting in `app_settings` and the capability record in `face_device_state`
— and never by loading the pipeline. Neither database's schema changed for it.

---

## 7. Media operations

Copy, move, delete, and create-folder all run through `MediaRepository`. Deletes and writes use
the Android 11+ `IntentSender` flow, wrapped behind `MediaPermissionHandler` so the ViewModel
stays free of `Activity` details.

The operand of a copy or move is resolved **explicitly**, not inferred from whichever selection
mode happens to be active — **[ADR-0003](decisions/0003-explicit-media-operation-target.md)**.
Resolution order is explicit target → media selection → folder selection, and the explicit target
must be cleared on both cancel and completion.

The action set available while media is multi-selected mirrors the full-screen viewer's —
**[ADR-0005](decisions/0005-selection-scoped-media-actions.md)**. "Mirrors" means the same actions
and icons, not the same placement.

Two operations delegate outward rather than doing the work in-process:

- **Use as background** hands the image to the system crop-and-set activity —
  **[ADR-0004](decisions/0004-delegate-wallpaper-setting-to-system-cropper.md)**. This is why the
  manifest carries a `<queries>` element.
- **Share** builds the intent in `ShareIntents`, attaching a `ClipData` because
  `FLAG_GRANT_READ_URI_PERMISSION` does not cover `EXTRA_STREAM`.

---

## 8. Media metadata and playback

GPS coordinates are read **lazily, per item, only when an info surface is shown** — never during a
`MediaStore` query, because Timeline View loads every item on the device —
**[ADR-0001](decisions/0001-on-demand-media-location-metadata.md)**. Images go through
`androidx.exifinterface`; videos through `MediaMetadataRetriever`.

Video playback uses **Media3 ExoPlayer pinned at 1.5.0**. That pin is load-bearing:
`media3-ui-compose` does not exist at 1.5.0, so the viewer's play/pause, progress slider, and time
labels are hand-rolled Compose controls over a `Player.Listener` plus position polling, and the
`PlayerView` runs with `useController = false`. Upgrading media3 is a decision to raise, not a
routine bump.

---

## 9. Localisation

The shipped locales are `LocaleHelper.supportedLanguageTags`: `en` in `values/`, and one
`values-*` folder for each other tag. A new user-facing string lands in every shipped locale in the
same change as its `R.string` reference. `AGENTS.md` *Strings and localisation* owns the rules —
folder naming, plural categories per locale — and is not restated here.

**Indonesian's folder is `values-in`, not `values-id`.** The tag stays `id`, which is what
`LocaleHelper` persists and the language picker shows, but Android rewrites `id` to the legacy `in`
before resource lookup. A `values-id` folder compiles and is never selected.

Two tests hold the folders and the tag list together. `LocaleResourceParityTest`, a plain-JVM unit
test, parses every `values*/strings.xml` against the base and against `supportedLanguageTags`.
`LocaleResolutionTest`, instrumented, resolves a string through `LocaleHelper.wrap` for every tag
on a device and fails on any that falls back to English — the one check that observes the `id`→`in`
rewrite rather than reasoning about it.

The language override is per-app and independent of the device language, applied through
`LocaleHelper.wrap` / `createConfigurationContext`. Two consequences follow:

- Play's per-language App Bundle splitting is disabled (`bundle.language.enableSplit = false`),
  or Play would only ever install the locales the device already had configured.
- A localised string must never be cached in anything that outlives `Activity.recreate()`.
  ViewModels resolve strings through a freshly wrapped `Context` at point of use.

Anything machine-parsed — coordinates, durations — is formatted with `Locale.US`.

---

## 10. Tech stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 (`material-icons-extended`) |
| Image loading | Coil 3 (`coil-compose`, `-gif`, `-video`, `-network-okhttp`) |
| Video playback | AndroidX Media3 ExoPlayer, pinned at 1.5.0 |
| EXIF | `androidx.exifinterface` |
| Async | Kotlin Coroutines + `StateFlow` |
| Background work | AndroidX WorkManager (`work-runtime-ktx`) — the constrained periodic media-index job and "Index now" |
| Billing | Google Play Billing (`billing-ktx`) — the tip jar |
| Media data | Android `MediaStore` — all media, and the favourite flag |
| App-owned data | Room, with KSP as the annotation processor — two databases: albums, and the media index |
| Face pipeline | OpenCV's published Android library (`org.opencv:opencv`), pinned at 4.13.0, untrimmed, with YuNet and SFace fp32 ONNX models bundled under `app/src/main/assets/face/` beside their licences, `NOTICE.txt` and `THIRD_PARTY_NOTICES.txt` — **[ADR-0023](decisions/0023-face-pipeline-models-runtime-and-delivery.md)**. The worker's face pass loads it, after the gates in §3, `MediaIndexingWork.kt`, and writes Tier C (§6) |
| Unit tests | JUnit4, MockK, Turbine, coroutines-test; Robolectric for the `data/` Android-boundary tests; `room-testing` and `inMemoryDatabaseBuilder` for the DAO; `work-testing` (`TestListenableWorkerBuilder`) for `MediaIndexingWorker` |
| Instrumented tests | Compose UI test + Espresso |

Versions are centralised in `gradle/libs.versions.toml`; app config in `app/build.gradle.kts`.

KSP needed `android.disallowKotlinSourceSets=false` in `gradle.properties`. The project uses
AGP 9's built-in Kotlin and applies no `kotlin-android` plugin, so KSP's source-set registration
is rejected without it. Removing that line breaks the build with an error that does not mention
KSP.

**The face pipeline's build wiring**, all in `app/build.gradle.kts` unless named:
- **arm64-v8a only, by packaging.** `packaging.jniLibs` excludes `libopencv_java4.so` and
  `libc++_shared.so` under `armeabi-v7a`, `x86` and `x86_64`; there is **no `abiFilters`**, which
  would make Play stop offering the app to those devices (ADR-0023 §2). They install and run without
  the face pipeline, and since step 3.6 the capability check records them incapable at once, under
  the key and without loading anything (`supportsFacePipelineAbi`), so the Face data area and the
  People picker stay hidden there and a people album shows the Device-incapable notice.
- **`verifyFaceModelChecksums`** hashes every file `assets/face/SHA256SUMS` lists and requires a line
  for each `*.onnx`; every `merge…Assets` task depends on it, so a changed model fails the build.
  `FacePipelineVersionTest` holds the catalogue's `opencv` version to `FACE_PIPELINE_OPENCV_VERSION`.
- **`app/proguard-face.pro`**, added to the release `optimization` block, keeps
  `org.opencv.core.CvException`: OpenCV's native code finds it by name, R8 cannot see that, and
  without the rule a native error reaches Kotlin as a plain `Exception`.
- **The models are read into memory from the APK**, never written to a file; the pipeline is loaded
  once per worker run and once by a capability check whose key is new — the worker's, or since step
  3.6 `FaceDataViewModel`'s at app open, whichever takes `faceIndexLock` first.
- **The merged manifests are unchanged by the dependency** — debug and release byte-identical to the
  build before it (ADR-0023, verify item 4).

---

## 11. Known debt

- **`GalleryViewModel` is by a wide margin the largest file in the app**, and still growing. The
  established escape hatches are a new state holder (`DisplayPreferencesState`,
  `FolderViewPreferencesState`, `MediaViewerState`) or a feature ViewModel
  (`CreateFolderViewModel`, `BillingViewModel`, `AlbumsViewModel`, `FavouritesViewModel`,
  `MediaIndexingViewModel`). Albums used the second and added no album state to its body.
- **Screen names lag the canonical ones** — see the caveat in §1. `FolderListScreen` and
  `CalendarGrid` still do; `DisplayMode` no longer does, having been renamed to
  `FOLDERS`/`TIMELINE`/`ALBUMS` when Albums View landed. `FolderListScreen` now hosts three views,
  so its name is less accurate than when this entry was written, not more.
- ~~**Back from Timeline View exited the app**, because the view switcher was a toggle with no
  notion of a home view.~~ Resolved when the switcher became a dropdown: back from Timeline or
  Albums View now returns to Folders View. Still true since the dropdown was replaced by the Main
  view bar on 2026-11-11, whose taps add no history; Back from the Settings screen lands on Folders
  View too, since ADR-0013 Amendment 3.
- **`SortType.DATE_TAKEN` reads the index for media, and not for folders or grouping.** The
  item-level sort was resolved at Stage 2 step 2.4: all four sites that sort media — Timeline View,
  Folder Detail View, Album Detail View and the New album screen — pass the index's projection into
  `GalleryTransformations.sortMedia`, which uses the captured date-taken where one exists and
  degrades to `dateAdded` for an item that is **not yet indexed** and for one that is **indexed with
  no date at all**. The two remaining uses still map to `dateAdded`: sorting *folders* by date taken
  reads `Folder.dateTaken`, which `MediaStoreDataSource` aggregates from each item's `dateAdded`;
  and `GroupByType.DATE_TAKEN_DAILY` / `_MONTHLY` group by `dateAdded`, so a date header can still
  disagree with the order of the items under it. Narrowed rather than struck, because the second of
  those is user-visible.
- **The indexing job's two API-level limits.** "Index now" is expedited only on API 31+; below it
  the request is ordinary one-time work, because expedited work there means a foreground service
  and a notification, and indexing shows none — the worker has no `getForegroundInfo`, as decided
  by [albums.md](../design/albums.md) §8 decision 23. The expedited request also carries **no**
  `requiresBatteryNotLow`, `requiresCharging` or `requiresDeviceIdle` — the platform accepts only
  network and storage constraints on expedited work and rejects the request otherwise — so the
  periodic job's constraints and the Index now request's are deliberately different sets, not a
  copy-paste omission.
- **Folders are aggregated by `BUCKET_DISPLAY_NAME`**, keeping the first path, so two folders with
  the same name collapse. Per-folder overrides and thumbnails inherit that limitation.
- **No pagination.** Timeline View holds every item on the device in memory.
- **Orphaned preference entries are not collected** after a folder is renamed or deleted. An
  album's display overrides are, when the album is deleted in the app — ADR-0021 point 7.
- **The override types say "folder" where they also mean album.** `FolderViewOverrides`,
  `FolderViewPreferencesStore`, `FolderViewPreferencesState` and
  `PreferenceApplyTarget.CURRENT_FOLDER` / `ALL_FOLDERS` serve Album Detail View too, where
  `CURRENT_FOLDER` means this album and `ALL_FOLDERS` every album and Favourites. The rename to
  container-neutral names was deferred, not rejected, by
  [ADR-0021](decisions/0021-per-album-display-preference-overrides.md) point 9: it widens the diff
  into `GalleryViewModel`, `FolderDetailScreen` and the instrumented tests for no behaviour.
  ADR-0006's accepted gaps now apply to albums as well: nothing marks an overridden folder or
  album, and neither has a reset-this-one action.
- **Favourites are absent on API 28–29.** The `MediaStore` favourite column arrived in API 30, so
  on older devices `FavouritesViewModel.isAvailable` is false and the Favourites tile and the
  favourite action are hidden rather than degraded. Accepted by
  [ADR-0011](decisions/0011-favourites-backed-by-mediastore.md); minSdk is 28.
- **Orphaned membership rows are never collected.** [ADR-0010](decisions/0010-album-membership-dual-key-identity.md)
  retains a membership whose media cannot be resolved, deliberately, so a file that comes back is
  not lost. Nothing ever deletes one, so a long-lived album accumulates rows for media that will
  not return. There is no cost yet at Stage 1 volumes and no policy for deciding when a row is
  truly dead.
- ~~**An album's cover cannot be chosen.** [ADR-0015](decisions/0015-album-cover-persisted-random-member.md)
  picks one member at random and persists it; the picker that would let the user change it is not
  built. A user who dislikes the cover has no way to change it short of deleting the album.~~
  **Resolved 2026-11-14, by [ADR-0015](decisions/0015-album-cover-persisted-random-member.md)
  Amendment 1**: Album Detail View's Album cover picker writes the existing `coverMembershipId`
  (§3, §6). Favourites still has no picker, and there is no way back to a random cover. Verified by
  unit tests, with the instrumented tests compiled but not run on a device.
- ~~**The `rule` column is read, and written only by tests.** `albums.rule` exists for
  [ADR-0012](decisions/0012-one-album-type-rule-additions-exclusions.md)'s rule half, and since
  Stage 2 step 2.5 `AlbumsViewModel` parses and evaluates it. But every album the app creates still
  stores `null`, and the Album type dialog's Automatic option stays disabled until step 2.7, which
  is the first step planned to write a rule from a shipped code path. Until then an automatic album
  exists only where a test or a restore put one. The engine is ready; the feature is not reachable.
  The engine is verified on hardware as far as tests can reach it: a device test drives live
  `MediaStore`, the production extractor and a real batch, and sees a rule album re-evaluated by the
  batch alone, and the notices' Compose tests pass on a device. **No notice has been seen in a real
  tile, row or Album Detail View of the installed app**, because nothing shipped can create a rule
  album to show one; [the Stage 2 plan](../design/albums-stage-2.md) carries that as a residual
  check.~~ **Resolved at Stage 2 step 2.7**: the Automatic option is enabled and the New automatic
  album screen writes a rule through `AlbumsViewModel.createAutomaticAlbum`. On 2026-11-11, on the
  SM-G990B, an installed-app walkthrough created a country album and a country + year album, saw
  the Incomplete-album notice on the tile until Index now and then the members and the coverage
  notices in Album Detail View; device tests checked every picker entry's count against the
  evaluator's members over the real index. **Not seen in the installed app**: the picker's group
  of places without a region and the location-access-off wording, which unit and Compose tests
  cover, and an unevaluatable album, which only data can produce. Kept struck through because
  those residual checks are recorded against it in the Stage 2 plan. What was seen on 2026-11-11 is
  history: since 2026-11-13 neither the Incomplete-album notice nor the coverage notices exist, and
  the location-access-off wording is shown by Settings' location row instead
  ([albums.md](../design/albums.md) §8 decision 29).
- **An automatic album's rule cannot be edited.** It is set at creation, and since 2026-11-14 it
  can be read in Album Detail View's Album info dialog but not changed there; rename, add, remove
  and delete work as on any album. A wrong rule is fixed by deleting the album and creating it again,
  which loses its manual additions and exclusions. Deliberate — [albums.md](../design/albums.md)
  §8 decision 26 — and any later editor inherits ADR-0017's never-rewrite rule.
- **A rule album does not say that it is waiting for the index.** `AlbumRuleEvaluator` still
  reports `WAITING_ON_INDEX` and `AlbumListItem` still carries it, but nothing draws it: a rule
  album whose matches are not yet indexed looks short or empty on its tile, its row and in Album
  Detail View, and nothing on the album says how much of the library has no usable date or location.
  The not-yet-indexed count and Index now are on the New automatic album screen's Index band and in
  Settings, and the coverage figures only in Settings. Accepted by the maintainer —
  [albums.md](../design/albums.md) §8 decision 29 records the cost against
  [ADR-0019](decisions/0019-offline-reverse-geocoding-for-region-albums.md)'s "must say why",
  which its Amendment 3 now meets in Settings, and
  albums.md §6's *Be honest about staleness*. The status was kept, so showing it again needs UI only.
- **Images indexed without `ACCESS_MEDIA_LOCATION` are never re-extracted after a later grant.** On
  API 29+ Tier A's `setRequireOriginal` read yields nothing without it — no location and no date —
  and the row is stored as final, because delta detection never re-extracts unchanged media. After
  a grant those images count in Settings' two coverage rows, as having no usable location and no
  usable date; before it, the location row says location access is off instead of counting. Known and not
  fixed: recovering them is a re-index, which is a decision — [albums.md](../design/albums.md) §10
  question 9.
- **Media in excluded folders counts in rule albums and in the picker's counts.** Both read the
  same gated live set, which ignores Folders View's exclusions; changing it for one reader and not
  the other would make a picker count disagree with the album it creates. Recorded as current
  behaviour, which the maintainer may revisit — [albums.md](../design/albums.md) §10 question 10.
- ~~**Location access is re-read only when an album recomputes.** `AlbumListItem.locationAccessGranted`
  is read inside the album computation, which runs on the album, membership, live-media and index
  inputs — none of which a permission grant changes. So granting `ACCESS_MEDIA_LOCATION` while the
  app runs shows in Album Detail View only after the next recompute. The picker does not share the
  lag: it re-reads on every computation and on `refresh()`.~~ **Resolved 2026-11-13, by removal**
  ([albums.md](../design/albums.md) §8 decision 29): no album and no picker reads location access
  any more. Its one reader is `MediaIndexingViewModel`, for Settings' location row, and the Settings
  screen re-reads it on every `ON_RESUME` — the event a grant made in system settings returns
  through — so the lag has no surface left. Kept struck through because the shape, a permission read
  inside a computation that no permission change triggers, will recur wherever the next reader is
  put.
- ~~**Index reconciliation still has its own copy of the live-media lookup.** `LiveMediaLookup` in
  `data/MediaIdentity.kt` was extracted at step 2.5 and is used by membership resolution and rule
  evaluation, but `MediaIndexReconciler` in `data/MediaIndexReconciliation.kt` still builds its own
  private by-URI and by-healing-key maps and runs the same direct-then-healing match — the second
  copy [ADR-0020](decisions/0020-automatic-albums-resolve-against-live-media.md) clause 3 forbids.
  It was out of step 2.5's scope and is not fixed. It is not a mechanical swap: reconciliation
  compares **raw** stored URIs, so moving it onto the lookup means choosing its URI key, and the
  canonical `canonicalMediaStoreUriForJoin` key the index join uses may be the right one — which
  changes which rows reconciliation treats as direct matches and needs its own tests.~~
  **Resolved at Stage 2 step 2.6**: the reconciler resolves through `LiveMediaLookup` keyed on
  `canonicalMediaStoreUriForJoin`, and holds no map of its own. **Canonical, because index rows and
  live media can spell one item's URI in different volume namespaces**: under the raw key a
  per-volume spelling of a live `external` URI missed the direct match, so it was rewritten as a
  move when its healing triple matched and deleted as gone when the file had also been edited.
  Under the canonical key it is a direct match and left alone;
  `MediaIndexReconciliationTest` pins which rows count as direct. The change made several Tier A
  tests vacuous, because their fakes seeded URI spellings production never produces — they were
  aligned with production's canonical URIs. `MediaIndexTierAIndexer.kt`'s own `liveByUri` is still
  a different, narrower join — selected rows to live items — and not a copy of this lookup.
- ~~**No unit test proves that a failed place resolve pass marks the indexing run failed.**
  `MediaIndexingWorker` rethrows a resolver failure into the catch that records the run as failed,
  and that is verified by reading only: the worker has no unit test, and writing one needs
  `androidx.work:work-testing`, a new dependency and so an `AGENTS.md` ask-before-doing item. A
  later change that swallowed the failure would leave Settings reporting success while no row was
  resolved — this stage's "indexed nothing and looked like it worked" shape — and nothing would
  fail.~~ **Resolved at Stage 2 step 2.6**: the maintainer approved `work-testing` as a test
  dependency, and `MediaIndexingWorkerTest` drives the real `doWork` through the worker's injectable
  constructor (§3). A resolver failure returns failure and marks the run failed without stamping a
  row — mutation-proven — alongside the success, cancellation and missing-permission paths. The
  run-state store records no error message by design; the message travels in the `Result`'s output
  data.
- ~~**App open hands `AlbumsViewModel` an empty media list before the real one.** The ViewModel
  starts with no live list and writes nothing back until it has one, but `MainActivity` and
  `FolderListScreen` forward `GalleryViewModel.allMedia` in a `LaunchedEffect`, and that flow starts
  as `emptyList()` until `MediaStore` has been read. So on app open the ViewModel receives an empty
  list first, which it cannot tell from an empty library: every membership is resolved as orphaned
  and that is written back, then reversed when the real list arrives, and a rule album can read as
  `COMPLETE` and empty for that window instead of waiting.~~ **Resolved at step 2.5's remediation
  round 1, by `GalleryViewModel.isMediaLoaded` (§3).** Both callers now forward only once the first
  `getAllMedia()` has returned, so the ViewModel's null-means-not-read guard engages in production
  for the first time. The fix went in the caller, as this entry predicted, because the ViewModel
  already told null from a list; the defect was the caller saying "loaded" too early. The gate is a
  load signal rather than `isNotEmpty()`, because a device with no media must still get an honest
  empty album. Kept struck through because it was this stage's signature failure — "looked like it
  worked" — in a fourth place, and an `emptyList()` placeholder elsewhere will invite it again.
  Unit-tested only: the gate lives in the two composables, and nothing run on hardware exercises
  that forwarding.
- **`MediaIndexingViewModel`'s figures read zero until media has loaded.** `MainActivity` forwards
  `allMedia` to `MediaIndexingViewModel.setLiveMedia` ungated. On a cold start the placeholder is a
  no-op — the ViewModel's live canonical-URI set already starts empty — but that empty set is the
  defect: it is read by one consumer only, the `uiState` combine that intersects index rows with
  live media, so until the first `getAllMedia()` returns, all four figures show zero on a library
  of any size: "0 not yet indexed" reads as "nothing to do", and the two coverage rows' zeros read
  as a library in which every item has a usable date and location. It reaches the user only if
  Settings is opened before loading finishes, and permanently if the first load throws. **It feeds
  no indexing input** — `MediaIndexingWorker` reads `MediaStore` itself and nothing passes the set
  to the scheduler — and nothing derived from it is persisted, so it self-corrects when media
  arrives. Found by reading at step 2.5; not device-observed and not fixed. Gating the figures on
  `isMediaLoaded` is the obvious shape.
  **The New automatic album screen's Index band reads the same state**, where zeros no longer
  misstate anything: zero not yet indexed and no pass running is the band's absent form, so they
  hide it. What they still cost there is the empty-list wording — with no pass running and nothing
  flagged as unindexed, an empty picker list says nothing was found rather than that it fills in as
  indexing finishes — and, because the Status area is drawn only with the band, the whole
  Status area. It is mitigated there rather than fixed: the band is drawn only once the picker is
  ready, which needs `AlbumsViewModel`'s join, which is built only after `isMediaLoaded` (§3) — so
  it never sees the placeholder, and if the first load throws the picker waits instead of showing a
  band. The mitigation is the screen's, not the ViewModel's, and it does not cover what `uiState`
  holds before its combine emits, in two cases. **First collection in a process:** the initial
  value is all zeros and `IDLE`, which hides the band — no count, no Index now — and gives an
  empty list the wording above. The
  ready gate beats it **by timing, not by guarantee** — a first open reaches ready only after the
  place-name file is read on a background thread, which in practice outlasts the combine's first
  emission, but nothing orders the two. **Any later open:** `uiState` is shared
  `WhileSubscribed(5_000)` with the default replay, so five seconds after its last collector leaves
  the upstream stops and the last value is kept. The next collector — the band or Settings — is
  handed that value until the combine re-emits, which needs all five of its sources to emit again;
  the band can be hidden, or show an old not-yet-indexed count with Index now offered, and Settings
  can show its old figures, while a periodic pass has since started. An Index now tapped then runs
  beside that pass, which the index tolerates (§6).
  The ready gate offers less here, because the names are already loaded and ready waits only for
  the lists to be recomputed. Found by reading; not device-observed and not fixed. **Follow-up:** an
  explicit "unknown" initial state, which neither screen reads as idle or as nothing to index,
  closes both cases; `replayExpirationMillis = 0` alone would only turn the second into the first.
- **The New album screen can open on an empty folder picker before media has loaded.** Its folder
  level reads `GalleryViewModel.newAlbumFolders`, which shows a spinner only while `allMedia` is
  empty **and** `uiState` is `Loading` — a test of emptiness, not of `isMediaLoaded`.
  `loadFolders()` sets `uiState` to success before `getAllMedia()` returns, and the flow is started
  lazily, so a screen first opened in that window gets a successful, empty picker until media
  arrives and it re-emits. If the first load throws, the picker stays empty rather than showing the
  error. Nothing is written, and an album created from it has no members, which is allowed anyway.
  Found by reading; not device-observed and not fixed.
- **If the first `loadFolders()` throws, albums read empty and nothing names the cause.**
  `isMediaLoaded` stays false, so `AlbumsViewModel` never receives a live list. Every rule album keeps
  reporting `WAITING_ON_INDEX`, which no UI shows since [albums.md](../design/albums.md) §8 decision
  29, so it reads as an empty album with no notice; until that decision it showed the
  Incomplete-album notice, which blamed indexing instead. Any album with stored additions, manual or
  rule, shows the Hidden-members notice, because nothing resolves against no list. Neither says that
  `MediaStore` was never read. Albums View is still reachable, because `AlbumsViewBody` does not
  check `uiState`. The load reruns only on Activity recreation or after the app's own file
  operations, not on `onResume`. What the null live list still buys over the behaviour it replaced —
  `COMPLETE` with zero members — is that nothing is written back; not fixed.
- **The Kotlin runtime artifacts are forced to 2.1.0** while the catalog pins Kotlin 2.2.10.
  `app/build.gradle.kts` holds three `resolutionStrategy.force` lines for `kotlin-stdlib`,
  `kotlin-stdlib-jdk8` and `kotlin-reflect`. They predate albums and were a workaround, not a
  decision, but KSP `2.2.10-2.0.2` and the Room code it generates now compile against a 2.2.10
  compiler and run against a 2.1.0 stdlib, so the gap matters more than it did. Nothing has failed;
  the reason the lines exist is no longer recorded anywhere, which is the actual debt. Establish
  whether they are still needed before the next Kotlin bump.
- **`DisplayPreferencesState`'s KDoc names three preference scopes; there are five.**
  `PreferenceScope` gained `ALBUMS` and `ALBUM_DETAIL` when Albums View landed, and the class
  documentation still lists only Folders View, Timeline View and Folder Detail View. A one-line fix
  in `app/src/main/java/com/davide/seddio/easygallery/ui/DisplayPreferencesState.kt`, recorded here
  so it is not lost.
- **`AlbumListItem` names two different things.** It is a composable in
  `ui/components/AlbumGridItem.kt` (one Album drawn as a row) and a data class in
  `ui/AlbumsViewModel.kt` (one Album's resolved state). Both live under `ui`, so a reader has to
  check the import to know which is meant. The UI vocabulary uses the composable sense. Renaming
  either is a vocabulary change and belongs to the Designer.
- ~~**"Index now" cancels the periodic indexing job until the app is next opened.** Both requests use
  the same unique work name, `media-index`, and the Index now path enqueues with
  `ExistingWorkPolicy.REPLACE`, so the one-time request takes the periodic request's place.~~
  **Resolved 2026-10-12, by commit `d68898d`.** The two requests now use separate unique work names,
  `media-index-periodic` and `media-index-now`, so Index now's `REPLACE` replaces only an earlier
  Index now and never the periodic job. The same change split liveness into three states (§3),
  because the old two-state liveness counted WorkManager's `ENQUEUED` as running — and a periodic
  job sits `ENQUEUED` between runs, so once it was scheduled the Settings screen said "Indexing…"
  with the Index now action disabled while no pass ran. The original entry is kept struck through
  because the fix was a choice between two unique names and a different existing-work policy, and
  two names was taken.
- **The periodic request's constraints are asserted by no test.** The instrumented test covers the
  Index now request's constraints and a real end-to-end worker run; `buildPeriodicRequest()` is
  verified by reading it. A later change could drop `setRequiresCharging` or `setRequiresDeviceIdle`
  and nothing would fail.
- **The Main view bar's colours are fixed, not theme roles.** `MainViewBarContainer`,
  `MainViewBarBorder`, `MainViewBarUnselected` and `MainViewBarIndicator` in `ui/theme/Color.kt`,
  with `BrandBlue` and `BrandBlueLight` for the selected item, will not follow a light theme or
  dynamic colour. Accepted while the theme is dark-only —
  [ADR-0013](decisions/0013-albums-as-third-display-mode.md) Amendment 2, point 9.
- **Two user-facing strings are English-only in nine locales.** `support_email_subject` and
  `error_no_email_app` exist in `values/` and in every locale added since, but not in the nine that
  predate them: `ar`, `de`, `es`, `fr`, `it`, `ja`, `ko`, `pt-rBR` and `zh-rCN`. They predate albums
  — added with the support-email feature — so they are not Stage 1 drift, but they break the rule in
  §9. `LocaleResourceParityTest` allowlists exactly those two keys in exactly those nine folders, so
  nothing fails today and any other missing key would. Translate them and delete the allowlist
  together.
  (`support_email_address` is `translatable="false"` and is correct as it is.)
- **Two accepted costs of Tier C's rules** — [ADR-0018](decisions/0018-media-index-in-a-second-database.md)
  Amendment 6, *Negative*. An edit that changes an image's pixels but keeps its healing triple —
  name, size and modified date — keeps its stored faces, which are then stale until the pipeline
  version changes. And on a device whose index uses a `PERSIST` rollback journal, a face-data
  deletion never reports scrubbed: the journal may keep page copies until later transactions
  overwrite them. ~~Which mode each phone actually uses is a step 3.7 check in
  [albums-stage-3.md](../design/albums-stage-3.md).~~ *Read at step 3.7:* both phones' indexes use
  `wal` with `normal` locking — the SM-G990B on 2026-11-22 and again after a real deletion on
  2026-11-23, the SM-G781B on 2026-11-23 — so neither phone meets the `PERSIST` case
  ([albums-stage-3.md](../design/albums-stage-3.md) *3.7*, G4, G9 and G10).
- **GIFs stay pending in Tier C on every pass.** The face pass skips them by design and writes no
  state, so each pass meets them again. The cost is a skipped candidate per GIF per run; a people
  album does not wait on one, because step 3.5 answers every item that is not an image as a settled
  no-match.
- **Grouping has two ceilings, measured but not enforced** (step 3.4's review, and step 3.7's G5c; the
  figures are in [albums-stage-3.md](../design/albums-stage-3.md) *3.4 — Built and verified* and
  *3.7*, G5c).
  *One giant component:* the per-component cap of 65,536 faces is an int-array limit, not a heap
  one, so a single component above roughly 10,500 faces fails with `OutOfMemoryError` rather than
  the fixed "too many" message. The data stays safe — nothing is written before the one transaction
  at the end, and the run fails with the worker's fixed message — but it fails every run. The
  synthetic components measured were at most 60 faces; the real 422-face library already had 88 in
  one. ~~A heap-based cap that fails with the fixed message is an open question for the maintainer.~~
  *2026-11-23:* accepted by the maintainer as debt, with no heap-based cap (*3.7*, L4).
  *The deadline covers the start only:* grouping starts within 8 minutes of the worker's start and
  is not stopped by the deadline after it, so after a face pass that used its 7-minute budget it has
  about 3 minutes before WorkManager's ~10-minute stop. Through the real store, on synthetic faces on
  the SM-G990B, grouping is quadratic — 75.7 s at 20,000 faces, the figure both capacities are scaled
  from — so it fits about **31,000 faces** after a full face pass and about **56,000** in a whole
  window. It writes once, at the end, so a grouping cut by the stop writes nothing that run: between
  those two figures no group appears or changes until the face pass's backlog is done and the pass
  ends early; above about 56,000 every run is cut and no group ever appears — safe, but the CPU is
  spent on every run. *Corrected 2026-11-22:* this entry's earlier estimate was scaled from the
  grouping core alone and overstated both figures. ~~Accepting the limit is open for the maintainer~~
  *2026-11-23:* accepted by the maintainer as a recorded limit, revisited on a real report (*3.7*, L11).
  *The store's own cost is small:* the paged read and the one-transaction write together took under a
  tenth of the total at every size measured, up to 37,000 faces, and the Java heap stayed well inside
  its 256 MB cap, so memory does not bind. A real library of that size, through the
  worker, is unmeasured — the largest real one is 422 faces.
- **The face pass and grouping have no thermal back-off.** Nothing in `app/src/main` reads the
  thermal status. On the SM-G781B a face pass reached thermal status 2 75 s in — charging, screen on,
  after a warm start — while a full run on the SM-G990B stayed at 0. The periodic job runs only
  charging and idle; Index now carries neither constraint (the indexing job's limits, above), so a
  user-started run can meet the SM-G781B's conditions. *2026-11-23:* accepted by the maintainer,
  revisited on a real report or a user complaint; the remedy then is a thermal check between pages
  that stops the pass as its budget does, or a smaller per-run budget
  ([albums-stage-3.md](../design/albums-stage-3.md) *3.7*, G10 and L13).
- **The capability record catches a crash only by counting.** A start is recorded before the check
  and two unfinished starts read as incapable, so a device whose OpenCV build crashes natively in
  the self-test is caught; a crash that only some images provoke, on some vendor's build, is caught
  only per image, by the three-attempt bound (§6).
- **OpenCV logs one line of its own on a native error.** `OpenCvFacePipeline.load()` calls
  `Core.setErrorVerbosity(false)`, which silences OpenCV's `cv::error()` line; its Java wrapper still
  logs "caught cv::Exception" under the tag `org.opencv.imgproc`, priority E, with OpenCV's assertion
  text and an OpenCV source path, and no switch for it was found. Observed on the SM-G990B for one
  provoked resize assertion, it held no URI, box or embedding; but OpenCV's value-printing checks can
  put operand values in that text, and the line is outside `FaceIndexLogLines` and outside what the
  logger test proves. ~~Whether to accept it is open for the maintainer~~ *2026-11-23:* accepted by
  the maintainer as a recorded limit, with no precondition checks
  ([ADR-0024](decisions/0024-face-data-disclosure-and-delete-switch.md) §6, note of 2026-11-22;
  [albums-stage-3.md](../design/albums-stage-3.md) *3.7*, L10).
- **The face pipeline's build wiring has two app-wide edges.** `verifyFaceModelChecksums` is wired
  to every task whose name starts with `merge` and ends with `Assets`, so an AGP rename of those
  tasks would silently unhook it. And the `libc++_shared.so` excludes under the three non-arm64 ABIs
  apply to the whole app: a future dependency that ships its own copy there loses it too.
- **OpenCV's published Gradle metadata gives the AAR a stale size.** Its `.module` file — Maven
  Central's own — lists 3,570,283 B for an AAR of 121,695,690 B; the AAR's `.sha1` and `.sha256`
  match. Nothing fails because of it; the project has no `verification-metadata.xml`, and adding one
  for this was judged out of proportion ([ADR-0023](decisions/0023-face-pipeline-models-runtime-and-delivery.md)).
- **The face models use most of their download budget.** What they and OpenCV add to the arm64
  download is 46,063,982 B against ADR-0023's 50 MB, unchanged since step 3.3; the whole arm64
  download is recorded beside it in ADR-0023's verify row 5 at every re-check. A model or OpenCV
  upgrade must re-measure it. The budget is worded as what the face path adds; read against the
  total, under 0.6 % of 50,000,000 B remained at step 3.7. ~~Which reading holds is open for the maintainer~~ *2026-11-23:* accepted by the
  maintainer — "50 MB" read as 50,000,000 B and the budget as what the face path adds, with the total
  recorded beside it at every re-check ([albums-stage-3.md](../design/albums-stage-3.md) *3.7*, L12).
- **Devices without arm64-v8a download the models and cannot use them.** The 36.1 MB of ONNX files
  are in the base APK on every ABI, while OpenCV's library ships for arm64-v8a only, so those
  devices pay the download and cannot run the pipeline. Since step 3.6 they are recorded incapable
  under the key, durably, by `supportsFacePipelineAbi`, before anything is loaded; the loader's
  `FacePipelineUnavailableException` is still not persisted, but such a device no longer reaches it.
- **The no-arm64 test lives in two places** (step 3.6's review). `FaceCapabilityChecker` records
  the answer, and `FaceDataViewModel` makes the same `supportsFacePipelineAbi` test first only to
  skip `faceIndexLock`. Both read the one function, so they cannot disagree on the ABI, but a change
  to when the lock is needed has to be made in the ViewModel as well.
- **An unknown capability counts as available to evaluation and as not offered to the picker.**
  `peopleAvailabilityOf` keeps counting unknown as available, because evaluation must never load
  the pipeline; `isPeoplePickerOffered` counts it as not offered, so no people rule can be *created*
  before the check has answered. The release gate step 3.5's review set — a people album waiting
  for ever on a 32-bit phone — was closed in step 3.6 by the no-arm64 record above. What remains is
  a people album restored onto an arm64 phone before its first app open: it waits until the check
  answers, which the app open starts.
- **The face evaluation query re-runs on index writes that are not face writes.** Its inner join
  to `media_index` makes Room invalidate it on Tier A and place writes too, so a people album is
  evaluated twice for such a batch, and the first Albums View emission waits for the first face
  query, because the album list combines every album's flow. One run costs about 20 µs per face,
  linear (step 3.7's G5c, synthetic). How often it runs during an
  indexing run is not measured: the face pass writes once per image, so it is invalidated up to once
  per image while something collects it, Room re-running the query once for the invalidations that
  arrive while it runs.
- **An oversized rule is refused, not thrown.** `AlbumRuleJson.encode` requires the document to fit
  the 16 KiB cap, which a people rule can reach with about 8 to 50 people depending on their anchors'
  escaped names. Since step 3.6 the picker stops at `MAX_PEOPLE_PER_ALBUM` (8, which fit even with
  255-character names), and `createAutomaticAlbum` refuses more than 8 and catches the `require`, so
  the cap is enforced twice. Raising 8 needs the long-name measurement again.
- **Face data's UI has edges that only a device can settle** (step 3.6's reviews; checks deferred to
  step 3.7 of [albums-stage-3.md](../design/albums-stage-3.md)). Several locales' Status notice and
  tile strings run 74–85 characters against about 60 in English, ~~and the People tab label in German
  and Italian,~~ unchecked at font scale 2.0 — *2026-11-23:* still not observed on a device, accepted
  by the maintainer (step 3.7, L6); ~~the Face licences dialog is untested at font scale 2.0
  and in landscape; that an outside tap does not close the disclosure is pinned only by its
  `DialogProperties` in a unit test, not on a device;~~ *settled on the SM-G990B (step 3.7, G4, G6,
  W1, W2):* the People tab label fits at font scale 2.0 in a 360 dp width in Italian, and in German
  since its translation was shortened to "Leute", which is on the native-speaker review list; the Face
  licences dialog keeps OK on screen at font scale 2.0 in portrait and in landscape; a tap outside the
  disclosure and Back both leave it showing; ~~the face crop's decode cost with 100 or more
  tiles, and its sharpness for small faces — 21 of the 104 groups measured have no member at least
  60 px wide — are unmeasured~~ *measured 2026-11-23 on the SM-G990B (step 3.7, G8):* a scroll of
  the People tab's 104 tiles had more than twice Folders View's share of janky frames, the cost on
  the UI thread (the figures are G8's), and the maintainer saw no stutter — a polish
  candidate (prefetching, or smaller crops), not fixed; by eye, 2 of the 104 crops were not
  recognisable, though 21 of the 104 groups have no member at least 60 px wide; and ~~that Coil's
  disk-cache directory is unchanged after the picker renders is asserted by the request's policy,
  not observed~~ Coil's disk cache is kept out by the request's policy, asserted by a test — on the
  device the cache directory never existed, so its unchanged listing could not show more. The
  translated privacy text — the
  disclosure, the Delete face data dialog, the Face data row and the three people Status notices —
  has had no native-speaker review.
- **Two small UI judgement items were left as they are.** The cleanup sentence under the Face data
  row is cleared by a `DisposableEffect` when Settings leaves composition, which a rotation also
  does, so a rotated Settings screen loses it; the start-up deletion repeats the scrub either way.
  And the order of the five licence files in the Face licences dialog is pinned only on the
  constant list, not on what the dialog draws.
- **`FaceGroupingWorkerMeasurementDeviceTest` can fail on a fresh install under a Gradle connected
  run**, with its `ENVIRONMENT` message: the runtime permission grant has not yet reached `MediaStore`
  in the already-running test process. A race in the test, not an app defect, seen on the SM-G781B;
  the test is opt-in, and runs with the media permissions granted beforehand through `am instrument`.
  *2026-11-23:* accepted by the maintainer, the test-side fix deferred — revisited when the test is
  next needed on a fresh install ([albums-stage-3.md](../design/albums-stage-3.md) *3.7*, G10 and L14).
