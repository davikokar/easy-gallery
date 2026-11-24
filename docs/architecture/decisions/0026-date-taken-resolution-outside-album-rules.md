# ADR-0026: Date taken outside album rules — the index, then MediaStore, then date added, and saying which

- Status: Active
- Date: 2026-11-24
- Adopted: 2026-11-24
- Decision makers: Drafted by the Planner for the Easy Gallery project maintainer, after an audit of
  every place the app uses a media date; the maintainer answered the audit's seven questions with the
  recommended defaults on 2026-11-24

> ~~**Nothing below is built yet.** The record is adopted ahead of the work it governs, so
> [ARCHITECTURE.md](../ARCHITECTURE.md) is not updated by this change: §3's `GalleryTransformations`
> and `MediaItem` rows, §2's sentence on what `MediaIndexDateTakenState` feeds, §8's Info overlay
> paragraph and §11's *`SortType.DATE_TAKEN` reads the index for media, and not for folders or
> grouping* entry describe the app as it is today, and the change that lands this work updates them
> and adds the link to this record. Until then, where the overview and this record disagree, the
> overview describes the code and this record describes what the code must become.~~
> **Built and verified 2026-11-24.** The code compiles, the unit suite passes, and
> `connectedDebugAndroidTest` passed on a device. What landed is described in
> [ARCHITECTURE.md](../ARCHITECTURE.md) §2, §3, §6, §8 and §11, which now link here. The build
> departs from the Decision's text in no choice the maintainer made; **Amendment 1** at the end
> records what it settled that the Decision left open, the one place its signatures differ from
> *Consequences*, an existing folder-sort quirk it kept, and which of the behaviour was observed on
> the device and which was not.

## Context

"Date taken" means three different things in the app today, and only one of them is a date taken.

- **Album rules read the index and nothing else.** A `time` clause, the New automatic album screen's
  Date picker, the People picker's "newest" order and Settings' date-coverage figure all read the
  media index's captured date (ADR-0018 Tier A, parsed from EXIF `DateTimeOriginal` or the video
  container). An item without one never matches a `time` clause, and there is no `dateAdded`
  fallback: [ADR-0017](0017-composable-album-rule-representation.md) Amendment 1, *Items with no
  date*, rules it out because a filter that invents a member is worse than one that is short.
- **The item-level Date taken sort reads the index and falls back to date added.**
  `GalleryTransformations.sortMedia` uses `dateTakenEpochMillis` — the indexed capture date, else
  `dateAdded * 1000` for an item indexed without a date or not indexed. The viewer's Info overlay
  shows the same value under the label "Date taken", so a date added is presented as a capture date
  with nothing to tell the two apart.
- **Grouping and the folder sort never read the index.** `groupMedia` re-sorts a Date taken daily or
  monthly grouping by `dateAdded` and keys its headers by `dateAdded`, so Timeline View's default —
  Date taken sort, grouped by Date taken daily — shows headers that can disagree with the order of
  the items under them, and discards the Date taken sort it was handed. Folders View's Date taken
  sort reads `Folder.dateTaken`, which `filterAndSortFolders` aggregates from `dateAdded`. The list
  row's date caption (`MediaListItem`) and the Media properties dialog's date row show `dateAdded`.

**The not-indexed window is long.** The index is filled by a constrained periodic job (charging,
idle, battery not low) or by Index now. [albums.md](../../design/albums.md) §8 decision 22 rules out
an index pass on app open. So after a fresh install, and for every new photo until the next pass,
every date-taken surface falls back to date added — a photo received today but taken in 2019 sits
under today's header.

**`MediaStore` has a date taken of its own, and it is cheap.** `MediaStore.MediaColumns.DATE_TAKEN`
is added in API 29 and is "milliseconds since 1970-01-01T00:00:00Z", the indexed value of
`MediaMetadataRetriever.METADATA_KEY_DATE` or `ExifInterface.TAG_DATETIME_ORIGINAL`; the reference
notes that an image must define both `TAG_DATETIME_ORIGINAL` and `TAG_OFFSET_TIME_ORIGINAL` for the
value to be reliable relative to the epoch (developer.android.com, read 2026-11-24). It is a column
of the query `MediaStoreDataSource` already runs, so reading it opens no file and costs nothing
[ADR-0001](0001-on-demand-media-location-metadata.md) forbids. The audit found:

- **On the device attached for the audit, 352 of 715 images had a `DATE_TAKEN`.** One device, not a
  rule; it shows that the column is populated for a large share of a real library before any index
  pass.
- **API 29+ semantics, from the audit's reading of AOSP's `ModernMediaScanner`:** the value is in
  milliseconds, and when no date can be derived the column is left null rather than filled with
  another date. It may differ from the index's value for a zone-less EXIF date: the index reads
  such a date in the device's zone at extraction (`parseExifDateTakenEpochMillis`), while the
  scanner estimates an offset of its own.
- **API 28 caveat:** the legacy provider can fill the column with the file's modified date when it
  has no capture date, so on API 28 a value cannot be told apart from a substitute. The constant
  `MediaColumns.DATE_TAKEN` is itself API 29.
- **The platform's own "approximate creation date" is not a date taken.** `MediaColumns.INFERRED_DATE`
  (API 36, R Extensions 15) is `DATE_TAKEN` when present and `DATE_MODIFIED` otherwise.

Decision 21 of [albums.md](../../design/albums.md) §8 rejected a second date source. Its evidence —
on an SM-G781B library, `DATE_TAKEN` was null for every item the index left undated, which are
overwhelmingly received media with no EXIF at all — is about items that will **never** have a date,
and about album membership. It does not speak to the not-indexed window, nor to labelling a date
added as a date taken. Both are what this record is about.

## Decision

1. **One resolution of "date taken" for every surface outside album rules.** In order:
   1. the media index's captured date, when the item is indexed with one (ADR-0018 Tier A);
   2. otherwise `MediaStore`'s `DATE_TAKEN`, read on **API 29+ only**, where a null or non-positive
      value is absent;
   3. otherwise the date taken is **unknown**.

   For display, sort, grouping and Timeline View, an unknown date taken falls back to the date
   added (`dateAdded * 1000`), the fallback the sort already uses. **The resolution says which it
   returned**: a real capture date (step 1 or 2) or the fallback (step 3). It is one pure,
   Android-free function in `data/GalleryTransformations.kt`, `GalleryTransformations.resolveDateTaken`,
   returning `ResolvedDateTaken(epochMillis: Long, isCaptureDate: Boolean)`.
   `dateTakenEpochMillis` remains as that function's `epochMillis`, so there is still one
   implementation. Step 2 applies to an item indexed **without** a date as well as to one not yet
   indexed.

2. **Album rules stay index-only.** A `time` clause, the Date picker's counts, the People picker's
   newest order and Settings' coverage figures keep reading the index through `DateTakenSortState`
   and `MediaIndexLiveJoin`, unchanged. ADR-0017 Amendment 1's rule — no date means no match, no
   fallback — is untouched, and so is decision 21 for album membership. What this record widens is
   decision 21's "no second date source is read", for display, sort and grouping only; albums.md §8
   records that as decision 51 with a pointer under decision 21.

3. **`MediaItem` gains `mediaStoreDateTakenMillis: Long? = null`**, a trailing defaulted field after
   `displayHeight`. `MediaStoreDataSource` adds `MediaStore.MediaColumns.DATE_TAKEN` to its media
   projection **only behind an explicit `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q` guard**, in
   the same shape as the existing orientation guard, and stores null below API 29, for a null column
   and for a value `<= 0`. The resolver therefore never reads `Build.VERSION`. Lint does not run here
   (`AGENTS.md` *Running Gradle*), so the guard is checked by reading it.

4. **Date-taken grouping uses the resolved date.** `groupMedia` gains a required
   `indexedDateTakenByCanonicalMediaUri: Map<String, Long?>` parameter. Date taken daily and monthly
   sort by, and key their headers by, the resolved `epochMillis` in the group order. How a grouping
   relates to a different chosen sort is **unchanged**: the grouping's re-sort by its own key is the
   order, as it is today. Last modified groupings and File type are unchanged.

5. **Folders View's Date taken sort uses the newest resolved date per folder.**
   `filterAndSortFolders` gains a required `indexedDateTakenByCanonicalMediaUri` parameter, and
   `Folder.dateTaken` becomes the newest resolved `epochMillis` among the folder's counted items, in
   milliseconds. The automatic folder thumbnail stays the newest item by `DATE_ADDED`
   ([ADR-0007](0007-persisted-folder-thumbnail-override.md)).
   *Amended 2026-11-24 — Amendment 1 §2:* the sort's direction is not changed, and Ascending lists
   the newest folder first, as it already did.

6. **A date added is never labelled a date taken.** The viewer's Info overlay and the Media
   properties dialog show a **Date taken** row only when `isCaptureDate` is true. When only the
   fallback is known they show a separate **Date added** row, with its own string
   (`properties_date_added`, "Date added: %1$s") and its own test tag. The list row's date caption
   in `MediaListItem` shows the resolved date with no label, as it shows a date today. The existing
   rule that a non-positive value is no date and hides the row applies to either row.

7. **No index run on first launch.** Decision 22 stands; `MediaStore`'s value covers the
   not-indexed window. **Accepted:** when the index reaches an item and replaces the `MediaStore`
   value, the two can disagree by a zone offset, so the item can move within the order or to the
   neighbouring day's header.

8. **Nothing else changes.** No new permission (`DATE_TAKEN` is not location data and needs no
   `ACCESS_MEDIA_LOCATION`), no new dependency, no index or album schema change, no migration, no
   new preference store.

## Alternatives Considered

**Hide the Date taken row when no capture date is known.** Honest, and the smallest change. Rejected
because it fixes one label and nothing else: Timeline View's headers, its order and the folder sort
would still read date added for every not-indexed item, and a fresh install would show no date at
all in the viewer for most of a library that `MediaStore` already dates.

**Read EXIF directly in the viewer, on demand, as ADR-0001 reads GPS.** Gives the viewer the exact
index value before indexing. Rejected because it covers the one surface that matters least for
order: sort, grouping and the folder sort would still disagree with the viewer, which is the defect.
It would also be a second EXIF date parser beside the indexer's, whose zone rule album rules depend
on, and a file open on every swipe with the overlay shown.

**Trigger an index pass when the app opens.** Closes the window properly. Rejected by decision 22,
whose reasons stand: battery and file-open cost on every start, and pressure towards expedited or
foreground work, which decision 23 rules out by refusing a notification.

**Use the modified date as the last fallback, before date added.** `DATE_MODIFIED` is what the
platform's `INFERRED_DATE` falls back to. Rejected because a modified date is not a capture date
either, and is often later than the date added: an edit, a rotation or a metadata write moves it, so
an item would jump in a date-taken order for something the user did today. Date added is the
fallback the sort already has and the one users have seen; changing it is a separate decision with
no evidence behind it.

**Prefer `MediaStore`'s value over the index's.** Available sooner and on every item at once.
Rejected because the index's value is the one album rules use, parsed under the zone rule ADR-0017
Amendment 1 states; putting it first means that, once indexed, the viewer, the sort and a time
album's membership agree about the same photo.

**Read `DATE_TAKEN` on API 28 too.** Rejected for the API 28 caveat in the Context: a substituted
modified date is indistinguishable from a capture date there, so it would be labelled Date taken
when it is not, which is the defect this record removes.

## Consequences

### Positive

- The Date taken sort, Date taken grouping, Folders View's Date taken sort, the list caption, the
  Info overlay and the Media properties dialog all read the same resolved date, so a header agrees
  with the items under it and the viewer agrees with the order it was opened from.
- On API 29+ most camera photos have a real date taken before the first index pass, which is the
  window decision 22 left open for display.
- The UI never calls a date added a date taken.
- The camera folder's implicit Date taken sort ([ADR-0008](0008-camera-folder-default-sort-order.md))
  and the slideshow's base order ([ADR-0025](0025-slideshow.md)) inherit the better date without
  change.

### Negative

- **Two date sources exist for display.** An item can move by a zone offset when the index replaces
  `MediaStore`'s value (Decision 7).
- **The viewer and Settings can disagree about one item.** Settings' without-date figure counts what
  the index has, because it measures what album rules can use; the viewer can show a `MediaStore`
  Date taken for an item that figure counts as without a date. The same holds for a `time` album,
  which will not contain an item the viewer dates from `MediaStore` until the index dates it.
- **API 28 gains nothing** before indexing; it keeps today's fallback behaviour.
- A genuine pre-1970 `DATE_TAKEN` is treated as absent by the `<= 0` rule. The same rule makes a
  container's zero date, if a provider stores it as a negative instant, absent rather than 1904
  ([ADR-0018](0018-media-index-in-a-second-database.md) Amendment 3).
- **Wider signatures.** `groupMedia` and `filterAndSortFolders` take the index map as a required
  parameter, `MediaPropertiesDialog`, `MediaListItem` and `ViewerInfoPanel` take the date they show,
  and the stateless `*Content` composables that render a list row or the Media properties dialog
  take the map — which breaks the instrumented tests that drive them, by design.
  *Amended 2026-11-24 — Amendment 1 §3:* `MediaPropertiesDialog` takes the map, not a date.
- One new string in every shipped locale.

## References

- [albums.md](../../design/albums.md) §8 decision 21 (widened by this record for display only),
  decision 22 (stands), decision 51 (this record's entry)
- [ADR-0017](0017-composable-album-rule-representation.md) Amendment 1, *Items with no date* —
  unchanged
- [ADR-0018](0018-media-index-in-a-second-database.md) — the index's captured date; Amendment 3, the
  zero container date
- [ADR-0001](0001-on-demand-media-location-metadata.md) — no eager per-file reads; this record adds
  none
- [ADR-0008](0008-camera-folder-default-sort-order.md) — its consequence that the camera default
  "sorts on `dateAdded`" has been history since Stage 2 step 2.4 and is further superseded in effect
  here
- [ADR-0020](0020-automatic-albums-resolve-against-live-media.md) — the index map is joined to live
  media by canonical URI, as the sort already does
- `MediaStore.MediaColumns.DATE_TAKEN` and `INFERRED_DATE`, developer.android.com, read 2026-11-24
- `app/src/main/java/com/davide/seddio/easygallery/data/GalleryTransformations.kt` —
  `dateTakenEpochMillis`, `sortMedia`, `groupMedia`, `filterAndSortFolders`, `DateTakenSortState`
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt` — `queryMedia` and
  its orientation guard
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/ViewerInfoOverlay.kt`,
  `MediaPropertiesDialog.kt`, `MediaListItem.kt`

## Amendment 1 — 2026-11-24 — What the build settled, and what was observed

> **Written by the Planner after the build, from the code** (`data/GalleryTransformations.kt`,
> `data/MediaItem.kt`, `data/MediaStoreDataSource.kt`, `ui/GalleryViewModel.kt`,
> `ui/components/ViewerInfoOverlay.kt`, `MediaPropertiesDialog.kt`, `MediaListItem.kt`, and the
> screens that call them). It changes no choice the maintainer made: the resolution order, the API 29
> floor, index-only album rules and the Date added label are as decided. The Decision's §5 and the
> *Wider signatures* consequence are marked in place to point here. The Planner ran no build and no
> device for this amendment; the results in §7 are the verification phase's, as reported.

### 1. The index map reaches four `GalleryViewModel` flows through one private bundle

`filteredFolders` (Folders View) and `newAlbumFolders` (the New album screen's folder level) already
combined five flows, the most the typed `combine` overloads take, so the index projection joins the
inner `combine` beside the exclusions, the show-excluded flag and the thumbnail overrides. The four
travel as `FolderListInputs`, a private data class at the end of `GalleryViewModel.kt`, which
replaces the `Triple` the three used to travel in. `groupedAllMedia` (Timeline View) and
`groupedFolderMedia` (Folder Detail View) take the map as a fourth flow. Album Detail View and the New
album screen group inside the screen, in a `remember` keyed on the map.

*Why no state holder:* the map is already a `GalleryViewModel` property, `indexedDateTakenByMediaUri`,
from `MediaIndexDateTakenState`; `FolderListInputs` is a value passed between two lambdas, and
`GalleryViewModel` gained no state.

### 2. Folders View's Date taken sort keeps the folder sorts' direction

`filterAndSortFolders` sorts Size, Last modified and Date taken by their value **descending** and
reverses that for Descending, so under all three **Ascending lists the largest or newest folder
first** — the opposite of the media sorts, where Ascending is oldest first. That predates this
record. The build changed the key the Date taken sort reads, not its direction, and a unit test pins
the direction as it is (*folder date taken sort orders by the newest resolved date and ascending lists
newest first like size*).

*Why not fixed here:* reversing it would flip what users see under three folder sorts, a change no
decision of this record asked for. It is recorded as known debt in
[ARCHITECTURE.md](../ARCHITECTURE.md) §11.

### 3. Who is handed the date, and who the map

- **`MediaListItem` takes `dateTakenMillis: Long`**, the resolved `epochMillis`, shown unlabelled as
  Decision 6 says. Its callers resolve it per row: `MediaList`, `GroupedMediaContent` and
  `CalendarGrid`, so `MediaList` and `GroupedMediaContent` take the map.
- **`ViewerInfoPanel` takes `dateTaken: ResolvedDateTaken?`**, already passed through the display
  rule of §4.
- **`MediaPropertiesDialog` takes the map, not a date** — the one departure from *Wider signatures*.
  The dialog is opened on a selection of any size and shows a date row only for a single item, which
  it resolves itself; a date parameter would be a nullable value every caller computes for one case.
- **`FolderListContent`, `FolderDetailContent`, `AlbumDetailContent` and `NewAlbumContent` take
  `indexedDateTakenByMediaUri`**, and their instrumented tests pass it, as *Wider signatures*
  expected.

### 4. One display rule for both labelled rows

`displayableDateTaken(ResolvedDateTaken): ResolvedDateTaken?`, in `ui/components/ViewerInfoOverlay.kt`,
replaces the Info overlay's `displayableDateTakenMillis` and is shared with the Media properties
dialog: it returns null for an `epochMillis` that is not positive, of either kind, so Decision 6's
hiding rule is one function. The overlay, the dialog and the list row all format with
`formatMediaDateTime`, in the configuration's locale. The rows are tagged `viewer_info_date_taken` or
`viewer_info_date_added`, and `media_properties_date_taken` or `media_properties_date_added`.

### 5. The strings

The dialog's unlabelled date string, `properties_date` ("Date: %1$s"), is removed from every shipped
locale. The dialog now uses `properties_date_taken`, which the Info overlay already used, or the new
`properties_date_added`. So one string was added and one removed, where Decision 6 and *Consequences*
counted only the addition.

### 6. `MediaStoreDataSource`

The guard is `hasDateTakenColumn()`, `SDK_INT >= Q`, beside `hasOrientationColumn`; below API 29 the
column is not projected and its index is -1, which reads as null. A null, zero or negative value is
stored as null. `getFolders` still aggregates `DATE_ADDED`, now converted to milliseconds so that
`Folder.dateTaken` has one unit; its list only seeds `GalleryViewModel`'s internal load state and no
view draws it — the folder lists that are drawn come from `filterAndSortFolders`.

### 7. What was verified, and what was not

- **Gate:** 1602 unit tests passed; `connectedDebugAndroidTest` ran 407 tests with no failure and the
  six skips already known.
- **Unit coverage:** the resolution order and its canonical-URI lookup, the Date taken sort, the
  folder aggregation, sort and thumbnail (`GalleryTransformationsTest`); Date taken grouping, daily
  and monthly, both directions, Today and Yesterday, and the groupings that ignore it
  (`DateTakenGroupingTest`, under Robolectric because `groupMedia` formats with
  `android.text.format.DateFormat`); the display rule (`ViewerInfoOverlayTest`); and the projection
  in `MediaStoreDataSourceTest`, **at API 30 and API 28 only**. API 29 — the guard's own boundary —
  has no test; the guard was checked by reading it.
- **Instrumented coverage:** `ViewerInfoPanelTest` and `MediaPropertiesDialogTest` for which row is
  shown, and the four `*Content` tests for the new parameter.
- **Observed on the device, with an empty index:** a Camera photo's Info overlay and Media properties
  dialog show **Date taken**, from `MediaStore`'s `DATE_TAKEN`; an undated WhatsApp photo shows
  **Date added** only; Timeline View's default grouping headers follow the capture date.
- **Not observed:** the shift Decision 7 accepts, after Index now dates an item; any behaviour on an
  API 28 device; and Folders View's Date taken sort on a device.

### Consequences

- **Positive:** `GalleryViewModel` gained no state; one function decides when either date row is
  hidden.
- **Negative:** a regression at exactly API 29 would be caught by no test; and Folders View's
  backwards Ascending under Size, Last modified and Date taken persists, now recorded.
