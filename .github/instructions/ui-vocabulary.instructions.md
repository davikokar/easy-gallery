---
description: Canonical names for Easy Gallery screens, views, areas, controls, and UI interactions.
applyTo: "app/src/**/ui/**/*.kt, app/src/**/MainActivity.kt"
---

# UI vocabulary

> **Maintenance — owner: Designer. Mode: in-change.**
> If you **add** a named screen, view, area, dialog, or control, name it here in the same change.
> You built it, you know it, and a vocabulary file that lags the UI misleads every agent it is
> injected into. **Renaming, removing, or resolving a collision between existing terms is the
> Designer's** — those need the whole vocabulary in view, not one entry.

Use these canonical names when discussing, implementing, and testing Easy Gallery's UI.
Prefer the product-facing name in prose and include the Kotlin symbol when extra precision is
useful. Preserve these meanings when adding new UI terms.
Treat aliases as input synonyms only. Use the canonical bold name in responses, implementation
notes, and tests.

Several Kotlin symbols predate the canonical names and have not been renamed. Where that is so,
the symbol is given in parentheses and the mismatch is called out. Use the canonical name in
prose; use the real symbol in code.

## Main views

The app has **five main views**, carried by three screens.

- **Folders View** (`FolderListScreen` with `DisplayMode.FOLDERS`): All device folders as a grid
  or list, each with a thumbnail and item count. The view the app opens on.
  Aliases: **gallery view**, **folder list**, **buckets**
- **Timeline View** (`FolderListScreen` with `DisplayMode.TIMELINE`, body drawn by
  `CalendarGrid`): Every image and video on the device, flat across folders and grouped under
  date headers. Aliases: **calendar view**, **all media**
- **Albums View** (`FolderListScreen` with `DisplayMode.ALBUMS`): The app-owned Albums and
  Favourites, shown as a grid or list. Aliases: **album list**, **albums grid**
- **Folder Detail View** (`FolderDetailScreen`): The images and videos inside one folder.
  Aliases: **folder contents**, **media grid**
- **Album Detail View** (`AlbumDetailScreen`): The images and videos referenced by one Album, or
  the system-backed media in Favourites. Aliases: **album contents**, **favourites contents**

Folders View, Timeline View, and Albums View are three modes of the same screen, chosen from the
**Main view bar** rather than by navigation. Folder Detail View is reached by
opening a folder; Album Detail View is reached by opening an Album or Favourites.

> **Naming caveat:** `DisplayMode.FOLDERS`/`TIMELINE`/`ALBUMS` now matches the canonical names.
> `FolderListScreen` and `CalendarGrid` still predate them: `FolderListScreen` now hosts three
> views, not merely a folder list, and `CalendarGrid` draws Timeline View. Use the canonical names
> in prose and the real symbols in code; do not invent a third set of names.

> **Album is not Folder.** An **Album** is a virtual, app-owned collection of references; an item
> can belong to any number of Albums, and deleting an Album never deletes its media. A **folder**
> is a filesystem / `MediaStore` container; each item belongs to one folder, and deleting the
> folder deletes files. Folder pinning, exclusion, display overrides, and thumbnail picking do not
> apply to Albums.

## Other screens

- **Full-screen viewer** (`FullImageScreen`): The swipeable single-item viewer for images and
  videos. Aliases: **viewer**, **full image screen**, **fullscreen**
- **New album screen** (`NewAlbumScreen`): The manual branch of album creation — names the Album
  and picks its members before it exists. Reached from the **Album type dialog**. Nothing is
  written until **Create**, so backing out leaves no empty Album behind (ADR-0016). Its parts are
  the **Album name field** (tagged `new_album_name_field`), the editable title in its top bar,
  pre-filled with the first unused `My album N`; the **Create album action** (tagged
  `new_album_create_button`), the bottom-right floating action button with a check icon; and the
  **New album hint** (tagged `new_album_subtitle`), the line below the top bar that shows the
  selected count, the prompt to choose media, or the name error. The **Create album action** is
  the one control whose node type changes with its state: while the name is blank or duplicate it
  is a disabled circular surface rather than a button, carrying the same tag but no click action.
  The body has two levels:
  the **New album folder level**, which lists folders, and the **New album media level**, which
  shows one folder's media and is the only level that selects. Selection accumulates as the user
  moves between folders. The **New album folder level** lists every non-excluded folder, in
  Folders View's order and layout but filtered by neither its search query nor its media-type
  filter. The top bar shows the **New album close button**
  (`new_album_close_button`) at folder level and the **New album back button**
  (`new_album_back_button`) at media level.
  Its test tags are `new_album_folder_grid` / `new_album_folder_list` once folders have loaded,
  with `new_album_folder_level` marking only that level's loading and error states; and
  `new_album_media_level` wrapping the media level, with `new_album_media_grid`,
  `new_album_media_list`, or `new_album_media_grouped` inside it. There is no tag that is present
  at the folder level in every state.
  Aliases: **album picker**, **album creation screen**
- **New automatic album screen** (`NewAutomaticAlbumScreen`, content `NewAutomaticAlbumContent`): The
  automatic branch of album creation — names the Album and describes it by place and date before it
  exists. Reached from the **Album type dialog**; it is its own branch in `MainActivity`'s chain, with
  `screenKey` `new_automatic_album`, and its saved state is dropped when it closes so a second visit
  never reopens the first visit's draft (ADR-0016 Amendment 2). Nothing is written until **Create**.
  The draft itself lives in `AlbumsViewModel`'s picker state, not in the screen. Its parts:
  - the **Album name field** (tagged `new_automatic_album_name_field`), the editable title in its top
    bar, pre-filled with the first unused `My album N`;
  - the **Create automatic album action** (tagged `new_automatic_album_create_button`), the
    bottom-right floating action button with a check icon. Like the **Create album action** it is a
    disabled circular surface with no click action, not a button, until there is a valid name **and**
    at least one choice;
  - the **match-count line** (tagged `new_automatic_album_match_count`), "N photos and videos will be
    in this album", which is plural-aware and announced as it changes. It shares its slot with the
    **New automatic album hint** (tagged `new_automatic_album_hint`), which shows the name error or,
    before anything is chosen, the prompt to choose a place, a date, or both;
  - the **choice chips** (tagged `new_automatic_album_place_chip` and `new_automatic_album_date_chip`),
    one per current choice, each clearing that choice when pressed;
  - two sections reached from tabs (`new_automatic_album_place_tab`, `new_automatic_album_date_tab`):
    the **Place picker** and the **Date picker**.

  The **Place picker** (list tagged `new_automatic_album_place_list`) opens on countries with counts.
  Opening a country shows an **All of {country}** row, its regions with counts, and a separate group
  headed **Places without a region** whose places are selectable one by one but never as a group;
  opening a region shows **All of {region}** and its places. The **Date picker** (list tagged
  `new_automatic_album_date_list`) opens on years, newest first; opening a year shows **All of
  {year}** and its months. A row that opens a list has a trailing chevron; a row that chooses is
  marked with a check once chosen. **The counts in each picker reflect the other picker's current
  choice.** Each picker keeps its own open level, and the top bar shows the **New automatic album
  close button** (`new_automatic_album_close_button`) until the open section has been drilled into,
  then the **New automatic album back button** (`new_automatic_album_back_button`), which steps out
  of that section only; so does the in-list back row (`new_automatic_album_place_back`,
  `new_automatic_album_region_back`, `new_automatic_album_date_back`).

  Before the pickers can list anything the screen shows one of three states instead: loading names
  (`new_automatic_album_loading`), names failed with **Retry** (`new_automatic_album_names_failed`,
  `new_automatic_album_retry`), or media still loading (`new_automatic_album_waiting`). A picker with
  nothing to list says so (`new_automatic_album_place_empty`, `new_automatic_album_date_empty`)
  rather than showing a blank. Row tags are `new_automatic_album_country_<code>`,
  `new_automatic_album_region_<admin1>`, `new_automatic_album_place_<name>`,
  `new_automatic_album_year_<year>`, `new_automatic_album_month_<1-12>` and
  `new_automatic_album_all_of_country` / `_region` / `_year`.

  Each picker's list opens with the **Status area**, a collapsible region made of the **Index band**
  and the picker's coverage notice (the **Location coverage notice** on the **Place picker**, the
  **Date coverage notice** on the **Date picker**, tagged `new_automatic_album_location_coverage` and
  `new_automatic_album_date_coverage`). It appears only once the picker is ready, at every drill
  level, and appears nowhere but these two pickers. Say "Status area" of the **New automatic album
  screen** in full: the **Media index area** of the **Settings screen** is a different region (its tag,
  `media_index_status_area`, shares the word), and "status notice" already means the **Newer-app
  notice** and **Incomplete-album notice**.
  - The **Status area header** (tagged `new_automatic_album_status_toggle`) is the area's first list
    item: a full-width row with the title **Index status** and an expand/collapse chevron at the end
    (`ExpandMore` / `ExpandLess`, which do not mirror). The whole row is the one Button-role target and
    exposes Expanded or Collapsed as its state. It is shown only when the area has any content —
    an **Index band**, a coverage notice, or both — so with nothing to show there is no header and
    no area. Aliases: **status toggle**, **status header**
  - The **Status area** (tagged `new_automatic_album_status_area`) is the header plus its content.
    It starts **expanded** each time the screen opens; collapsed, only the header remains. It is one
    flag shared by both pickers: it survives a tab switch, a drill level, and rotation, and is
    dropped with the screen's saved state when the screen closes (ADR-0016 Amendment 2), so a
    second visit opens expanded again.
  - The **Index band** (tagged `new_automatic_album_index_band`) is the first thing inside the
    expanded area, ahead of the coverage notice. It is **not a notice**: it is not one of the
    coverage notices and has no tinted container. Say "Index band" in full; a bare "band" is
    ambiguous. It is **shown only while something needs saying**: live media is not yet indexed
    (a count X > 0), or an indexing pass is running. Otherwise it is absent, not blank. It has two
    forms, and the same tag `new_automatic_album_index_now` marks the action in both:
    - **Idle, X > 0**: one row — an icon-only **Index now action** (`AutoMirrored.Filled.ManageSearch`,
      48dp, no label) at the start side, then the plural-aware text "X media files are not indexed
      yet. Click to index". The whole row is one Button-role target, so the text is not a dead zone.
      Its accessibility label comes from the **Index now** string. It starts a user-requested scan
      exactly as the **Index now action** of the **Settings screen** does.
    - **Running**: not a button. A non-tappable indeterminate progress indicator replaces the icon,
      beside the running text, with no count. It says only that work is under way, because starting
      again would cancel the running scan; the measured progress stays in the **Media index area**
      of the **Settings screen**.

    The only figure the **Index band** carries is X, the plain count of live media not yet indexed
    — never a percentage, never progress. Opening the screen never starts indexing. When nothing is
    indexed, or while indexing is running, a picker with nothing to list says so in its own wording
    under the same `new_automatic_album_place_empty` / `new_automatic_album_date_empty` tags.
  Aliases: **automatic album picker**, **automatic album creation screen**
- **Settings screen** (`SettingsScreen`): App preferences, language, support, legal, and the tip
  jar. Its **Media index area** (`MediaIndexSettingsArea`, tagged `media_index_status_area`) reports
  current scan progress, items indexed with a date, items indexed with no date, deferred items,
  and the last successful scan. The **Index now action** (tagged `media_index_now_action`) starts
  a user-requested scan and is disabled without a click action while indexing is already running.
  The **New automatic album screen** offers the same action in the **Index band** of its **Status
  area**, as an icon-only row, only while some media is not yet indexed.
- **Manage excluded screen** (`ManageExcludedScreen`): Lists excluded folders so they can be
  brought back.
- **Permission denied screen** (`PermissionDeniedScreen`, in `MainActivity.kt`): The message and
  retry action shown when media access is refused.

Use **screen** only for a full destination in `MainActivity`'s branch chain. Use **view** for the
five main views above, **dialog** for modal content, and **area** for a region within a screen.

## Screen structure

Each detail screen and the shared three-view screen are built as a pair:

- **Screen composable** (`FolderListScreen`, `FolderDetailScreen`, `AlbumDetailScreen`): Reads the
  ViewModel and binds the view's `PreferenceScope`.
- **Content composable** (`FolderListContent`, `FolderDetailContent`, `AlbumDetailContent`):
  Stateless, takes plain values plus callbacks. This is what the instrumented tests drive, and
  every parameter is passed by name there, so adding or renaming one breaks those tests by design.

## Top bars

A view shows exactly one top bar at a time. The order it resolves in is given per view.

- **Browse top bar** (`SearchTopBar`): The default top bar. Shows the title, the Search button,
  and the Overflow menu; in search mode the title is replaced by the **Search field**. Used by all
  five main views. On Folders View, Timeline View, and Albums View the title is the app name,
  Easy Gallery, preceded by the **App logo** (tagged `main_view_app_icon`); the view is named by
  the selected tab of the **Main view bar**, not by the title, and there is no view-switching
  dropdown. Folder Detail View and Album Detail View keep their own titles (the folder's or the
  Album's name) and show no logo.
- **Folder selection top bar** (`SelectionTopBar`): Shown while folders are multi-selected in
  Folders View. Carries the selection count and the folder actions.
- **Media selection top bar** (`MediaSelectionTopBar`): Shown while media items are
  multi-selected in Timeline View or Folder Detail View. Carries a close button, the selection
  count, and an Overflow menu holding **all** media actions.
- **Album selection top bar** (`AlbumSelectionTopBar`): Shown after long-pressing an Album in
  Albums View. Carries Rename and Delete; Favourites cannot enter this mode.
- **Album media selection top bar** (`AlbumMediaSelectionTopBar`): Shown while media items are
  multi-selected in Album Detail View. Carries album membership and media actions.
- **Thumbnail picker top bar** (`ThumbnailPickerTopBar`): Shown while the Folder thumbnail picker
  is open in Folder Detail View. Takes precedence over both selection bars.

Shared parts:

- **Overflow menu**: The three-dot menu in a top bar. Qualify it by bar when needed, such as
  **media selection overflow menu**. Every item carries a leading icon and no tint.
- **Back button**: The top-bar control that returns to the previous destination.
- **Search button** / **Search field**: Open live name filtering, and the field it filters from.
  Search is per view and deliberately not persisted.

## Main view bar

- **Main view bar** (`MainViewBar`, tagged `main_view_bar`): The persistent three-tab bar at the
  bottom of the screen that switches between Folders View, Albums View, and Timeline View. It
  replaced the title dropdown (ADR-0013 Amendment 2), which no longer exists; the top bar's title
  is now the app name. Stateless: the caller passes
  `selectedTab` and `onTabSelected`. Never call it the "bottom bar" (that is the **Viewer bottom
  bar**) or the "navigation bar" (that is the system bar directly beneath it); it pads itself for
  the system navigation bar and the side insets.
- **Main view tab** (`MainViewTab`; tagged `main_view_tab_folders`, `main_view_tab_albums`,
  `main_view_tab_timeline`): One tab of the bar — an icon over a one-line label, reusing the
  titles Folders, Albums and Timeline. Always in that order, which is not `DisplayMode` order. The
  selected tab has a tinted pill behind its icon and is exposed to accessibility as a selected
  tab. Tapping the selected tab still calls back.
- The bar is **shown** on Folders View, Albums View, Timeline View, Folder Detail View, and
  Album Detail View (including Favourites), and stays visible during selection and search; a tab
  tap cancels both and switches. It is **hidden** on the Full-screen viewer, the Folder thumbnail
  picker, the New album screen, the New automatic album screen, the Settings screen, the Manage
  excluded screen, and the Permission denied screen. On a detail screen the selected tab is the
  main view that owns it: Folders for Folder Detail View, Albums for Album Detail View.

## Folders View

- **Folder grid** (`FolderGrid`) and **Folder list** (`FolderList`): The two layouts of the view,
  chosen by the View type setting. Tagged `folder_grid` / `folder_list`.
- **Folder tile** (`FolderGridItem`) and **Folder row** (`FolderListItem`): One folder in the
  grid or the list. Tagged `folder_tile_<path>`.
- **Folder thumbnail**: The image shown on a Folder tile. It is the newest item in the folder
  unless a **Folder thumbnail override** has been set (ADR-0007).
- **Item count**: The number of media items in a folder, shown on its tile.
- **Pinned folder**: A folder the user has pinned to the top of Folders View. Marked with the
  **Pin badge** (tagged `pin_icon`).
- **Excluded folder**: A folder hidden from Folders View and Timeline View. Restored from the
  Manage excluded screen, or shown temporarily via **Show excluded**.

## Timeline View

- **Grouped media content** (`GroupedMediaContent`): The grouped body of the view.
- **Group header** (`DateHeader`, `GroupHeader`): The sticky label above each group — a date, a
  month, or a file type. Today and yesterday are labelled in words.
- **Group by**: What the headers divide on — none, date taken daily or monthly, last modified
  daily or monthly, or file type.

## Folder Detail View

- **Media grid** (`MediaGrid`) and **Media list** (`MediaList`): The two layouts of the view.
- **Media tile** (`MediaGridItem`) and **Media row** (`MediaListItem`): One image, GIF, or video.
  Tagged `media_tile_<uri>`.
- **Selection checkmark**: The check drawn on a selected item, tagged `selected_checkmark`. On a
  Media tile it sits in the top-right corner; on a Media row it is centred. These are
  deliberately different — do not align them.
- **Favourite indicator**: The filled heart that marks a favourite Media tile or Media row in
  Folder Detail View, tagged `favourite_indicator`. It is a read-only state indicator, not the
  interactive **Favourite heart** action in the Full-screen viewer or selection overflow menus.
- **Duration badge**: The running time drawn on a video's Media tile.
- **Folder thumbnail picker**: The mode, entered from the browse overflow menu, in which tapping
  an item chooses the folder's cover. It is **draft and commit**: a tap only drafts, the top-bar
  icon swaps from Close to a back arrow once a draft exists, the back arrow commits, and Close
  cancels. The current override is pre-selected for display only and is never re-committed by
  backing out (ADR-0007, Amendments 1 and 2).

## Albums View

- **Album** (`StoredAlbum`): A named, virtual, app-owned collection of media references. Creating,
  renaming, or deleting one never creates, renames, moves, or deletes media files. An item may
  belong to any number of Albums. Aliases: **collection**
- **Album grid** and **Album list**: The two layouts of Albums View, chosen by the View type
  setting.
- **Album tile** (`AlbumGridItem`) and **Album row** (`AlbumListItem`): One Album in the grid or
  list. Both are tagged `album_tile_<id>`.
- **Album cover** (`AlbumCover`): The media thumbnail representing an Album. It is selected at
  random when the Album first gains a member and persisted; removing that member reselects it.
  Empty or fully unavailable Albums show the placeholder. There is no Album cover picker.
- **Add album button** (tagged `add_album_button`): The circular bottom-right Albums View
  control that opens the **Album type dialog**. It is shown only in Albums View while
  **Album selection mode** is not active, and mirrors the **New album** overflow action.
- **Manual album** and **Automatic album**: The two kinds offered by the **Album type dialog**. A
  Manual album's members are chosen by the user on the **New album screen**; an Automatic album
  fills itself from a rule — a place, a date range, or both — chosen on the **New automatic album
  screen**. An Automatic album's rule is set at creation and cannot be edited afterwards; rename, add,
  remove and delete work as on any other Album (albums.md §8 decision 26). The distinction is a
  product one — in storage there is one Album type (ADR-0012).
- **Favourites** (`FavouritesAlbumGridItem`, `FavouritesAlbumListItem`): The fixed, system-backed
  collection of media marked with `MediaStore.IS_FAVORITE`. It cannot be created, renamed, or
  deleted, and is unavailable on API 28–29.

> **Favourite is not Pinned.** **Favourite / Unfavourite** marks individual media items and
> controls Favourites. **Pin / Unpin** only changes the ordering of folders in Folders View. Never
> use either word for the other operation.

> **A floating action button overlays the scrolling content, and its clearance belongs to the
> scrolling container.** The **Add album button**, the **Create album action** and the **Create
> automatic album action** all float over
> a scrolling body — Album tiles, Folder tiles, and Media tiles pass beneath them — and the
> clearance that keeps the last row reachable is `contentPadding` on the lazy grid or list, never
> an empty strip reserved in the body layout. A reserved strip is wrong twice over: the button
> stops floating over anything, and the band it holds back is dead background at every scroll
> position, including the top, where nothing needed clearing. Both controls have shipped covering
> their own last row, so **prove the clearance by comparing bounds**. Asserting that the last item
> exists, or is displayed, passes whether or not the button is sitting on top of it.

## Album Detail View

- **Favourite heart**: The filled or outlined heart that favourites or unfavourites a media item.
  It is available in the Viewer action row and media-selection overflow menus when Favourites is
  supported. Aliases: **heart**, **favourite button**
- **Hidden-members notice** (tagged `album_hidden_items_notice`): The informational notice shown
  when an Album has stored members that cannot currently be resolved against `MediaStore`, such as
  after restore or while an SD card is absent. It means those references are retained and waiting;
  it is never an error, proof of deletion, or data loss. A fully unresolved Album may therefore
  appear empty while this notice is shown. It counts stored additions only; a rule match has no
  stored row and can never make it appear.
- **Newer-app notice** (tagged `album_rule_unevaluatable_notice`; `AlbumEvaluationStatus.UNEVALUATABLE`):
  The notice shown when an Album's automatic rule was written by a newer version of the app and
  this build cannot read it. It says only the items added by hand are shown and that updating the
  app is what changes that — unlike the **Hidden-members notice**, it does not promise anything will
  reappear. It is drawn on the **Album tile**, the **Album row**, and in Album Detail View, with a
  short wording on the tile and row and the full wording in Album Detail View.
- **Incomplete-album notice** (tagged `album_rule_waiting_notice`; `AlbumEvaluationStatus.WAITING_ON_INDEX`):
  The notice shown when an automatic Album may be incomplete because some photos and videos have
  not been scanned yet. It makes **no promise that a scan is running** — it holds whether indexing
  is in progress, paused, or not started — and points to the **Media index area** of the
  **Settings screen** for the current state. It shows **no count, percentage, or progress
  indicator of its own** — the **Media index area** is the only place progress is measured. The
  **Index band** of the **New automatic album screen** shows one plain count (media not yet
  indexed) and an indeterminate indicator while running, never a percentage or measured progress,
  so a second count here, measured differently, could contradict it. It is not the **Hidden-members notice** (stored references that cannot be
  resolved) or the **Newer-app notice** (a rule this build cannot read). Same three surfaces and
  the same short/full split as the **Newer-app notice**. The Album Detail View wording names the
  **Media index area** and Settings; the tile and row wording names Settings only. Renamed from
  "Still-scanning notice", which implied a running scan; that name is now an alias only, and the
  `testTag` keeps its older `waiting` name because tests depend on it.
  Aliases: **Still-scanning notice**
- **Location coverage notice** (tagged `album_location_coverage_notice` in Album Detail View;
  `new_automatic_album_location_coverage` in the **Place picker**) and **Date coverage notice**
  (`album_date_coverage_notice`; `new_automatic_album_date_coverage` in the **Date picker**): Library-wide
  *counts* of photos and videos the index reached and found nothing usable in — no usable location,
  or no capture date — which an album with a `region` or a `time` clause leaves out (albums.md §8
  decisions 21 and 25). They appear in **exactly two places**: the pickers, inside the collapsible
  **Status area** below the **Index band** (hidden while it is collapsed), and Album Detail View.
  **Never on an Album tile or Album row, and never in Settings.** They are
  counts, not percentages; an item not yet scanned is in neither count, and the wording never says
  "yet", "still" or "scanning", because waiting will not change what they count. When location access
  is off (API 29+ without `ACCESS_MEDIA_LOCATION`) the Location coverage notice says so instead of
  giving a count. Neither appears when its count is zero, on a Manual album, or on an album with the
  **Newer-app notice**. In Album Detail View they are announced to screen readers; in the pickers
  they are not.
- Either notice may appear together with the **Hidden-members notice** on the same Album. In Album
  Detail View they stack as separate notices in a fixed order: the **Newer-app notice** or
  **Incomplete-album notice** first, then the **Location coverage notice**, then the **Date coverage
  notice**, and the **Hidden-members notice** last. The two status notices never appear together,
  because an Album has one evaluation status; the two coverage notices can, when the rule has both
  a place and a date. On the tile and row the status notices are also announced as the tile's state
  description, because its content description is the Album name alone; the coverage notices are not
  shown there at all.
- **Add to album** (`AddToAlbumDialog`): Add the selected media references to an existing Album, or
  start creating a new Album. This never copies or moves files.
- **Remove from album**: Remove selected media references from the current Album without deleting
  the media. In Favourites, the corresponding action is **Unfavourite**, because Favourites is
  system-backed rather than an app-owned Album.

## Full-screen viewer

- **Pager**: The horizontal swipe between items in the current media list.
- **Viewer bottom bar**: The bar below the media, holding the Info overlay, the Video controls
  when the page is a video, and the Viewer action row. It occupies real layout space rather than
  covering the media, and the whole bar collapses in immersive mode.
- **Viewer action row**: Delete, Share, Rotate (images only), Info, the Favourite heart when
  supported, and the **Viewer overflow menu** (Copy to, Move to, Use as background, Add to album).
- **Immersive mode**: The state in which the Viewer bottom bar is hidden and the media fills the
  screen. Toggled by a single tap on either a photo or a video.
- **Info overlay**: The name, path, and GPS panel toggled by the Info button. It is transient
  viewer state and is never the persisted `showInfo` preference, which drives grid captions.
- **Video controls** (`VideoPlaybackControls`): The progress slider, play/pause, restart, and
  elapsed/total labels shown for a video page. They are hand-rolled Compose controls, not
  media3's `PlayerView` controller.
- **Zoomable image** (`ZoomableImage`): The pinch-to-zoom, pan, and rotate surface for a photo.
  Only images have double-tap-to-zoom.

## Dialogs

- **Sort dialog** (`SortDialog`): Sort type and order.
- **Column count dialog** (`ColumnCountDialog`): Grid column count.
- **Group by dialog** (`GroupByDialog`): Grouping and group order.
- **Filter media dialog** (`FilterMediaDialog`): Which media types are shown.
- **View type dialog** (`ViewTypeDialog`): Grid or list.
- **Create folder dialog** (`CreateFolderDialog`): New folder name plus a path browser.
- **Destination picker** (`DestinationFolderPickerDialog`, built on `FolderBrowser`): Chooses the
  target folder of a copy or a move. Its parts are the **Breadcrumb** (`Breadcrumb`), the path
  trail across the top, and the **Browser row** (`FolderBrowserItem`), one subdirectory.
- **Media properties dialog** (`MediaPropertiesDialog`): Name, size, date, and — for a single
  item only — GPS.
- **Folder properties dialog** (`PropertiesDialog`, in `ui/FolderDialogs.kt`): The properties of
  one or more selected **folders**. A different dialog from the Media properties dialog; never
  call either one just "the properties dialog".
- **Language selection dialog** (`LanguageSelectionDialog`): The in-app language override.
- **About dialog** (`AboutDialog`): The app information shown from the Settings screen.
- **Place data dialog** (`PlaceDataDialog`, content tagged `place_data_notice`): The full
  GeoNames notice opened from the **Place data row** — creator and source, licence name and link,
  the statement that the data was modified, and GeoNames' as-is disclaimer. It scrolls, closes with
  OK, and shows each URL as tappable text, tagged `place_data_source_link` and
  `place_data_licence_link`. "GeoNames", "CC BY 4.0" and the URLs are never translated.
  Aliases: **attribution dialog**, **GeoNames notice**
- **Add excluded folder dialog** (`AddExcludedFolderDialog`): Excludes a folder by browsing to it
  from the Manage excluded screen.
- **Album type dialog** (`AlbumTypeDialog`): Chooses between a **Manual album** and an
  **Automatic album** before anything is created. Tagged `album_type_manual` and
  `album_type_automatic`. Opened by the **Add album button** and the **New album** overflow
  action; Manual leads to the **New album screen** and Automatic to the **New automatic album
  screen**. Both options are always enabled.
- **Create album dialog** (`CreateAlbumDialog`): Names a new Album. Reached only from **Add to
  album**, where the media is already chosen; Albums View uses the **Album type dialog** instead.
- **Rename album dialog** (`RenameAlbumDialog`): Changes an Album's name, never a folder or file.
- **Delete album dialog** (`DeleteAlbumDialog`): Confirms deleting an Album and its references;
  the media files remain untouched.
- **Add to album dialog** (`AddToAlbumDialog`): Chooses an Album for selected media, with an action
  to create a new Album.
- **Delete confirmation**: The only gate before a delete. The media one shows the item count.

The five display-preference dialogs are **draft and commit**: a selection changes nothing until
OK is pressed (ADR-0006).

- **Apply only to this folder**: The checkbox on those dialogs, shown **only** in Folder Detail
  View, checked by default. Unchecking it and confirming writes the global default and clears
  that setting's override on every folder.

## Settings screen

- **Settings section** (`SettingsSection`): A labelled group of related rows, such as General or
  Legal.
- **Settings row** (`SettingsItem`): One tappable row within a Settings section.
- **Change language**: The row that opens the Language selection dialog.
- **Place data row** (tagged `place_data_row`): The last row of the Legal section, under Privacy and
  Terms. Its subtitle is the short credit, visible without a tap; tapping it opens the **Place data
  dialog**. It is the app's attribution surface for the GeoNames place data and must survive any
  reorganisation of Settings (ADR-0019 Amendment 1). Aliases: **GeoNames credit**, **attribution row**
- **Tip jar**: The in-app purchase that supports the developer, driven by `BillingViewModel`.
  It is the app's only use of the network or a Play account.

## Manage excluded screen

- **Excluded folder row** (`ExcludedFolderItem`): One excluded folder, with the control that
  brings it back.
- **Show excluded**: The browse overflow item that reveals excluded folders temporarily. It is
  deliberately transient and is never persisted.

## Preference scopes

- **Preference scope** (`PreferenceScope`): Which view a display preference belongs to —
  `FOLDERS`, `TIMELINE`, `FOLDER_DETAIL`, `ALBUMS`, or `ALBUM_DETAIL`. Each main view owns an
  independent set.
- **Albums preference scope** (`PreferenceScope.ALBUMS`): Sort, layout, and column settings for
  Albums View only.
- **Album Detail preference scope** (`PreferenceScope.ALBUM_DETAIL`): Sort, layout, columns,
  grouping, filtering, and info settings for Album Detail View only.
- **View preferences** (`ViewPreferences`): The bundle itself — sort type, sort order, view type,
  columns, group by, group order, media types, show info.
- **Per-folder override** (`FolderViewOverrides`): A sparse set of Folder Detail preferences
  bound to one folder path, merged over the global ones on read.
- **Camera default**: The implicit date-taken-descending sort applied to the camera folder. It
  sits between the global preference and the per-folder override, and is never persisted
  (ADR-0008).

## Modes

These are mutually exclusive and clearing them is bidirectional. Entering one must leave the others.

- **Browse mode**: The default. The browse top bar is showing.
- **Folder selection mode**: Folders are multi-selected in Folders View.
- **Media selection mode**: Media items are multi-selected in Timeline View or Folder Detail View.
- **Album selection mode**: One Album is selected for Rename or Delete in Albums View. Favourites
  cannot enter it.
- **Album media selection mode**: Media items are multi-selected in Album Detail View.
- **Thumbnail picker mode**: The folder cover is being chosen in Folder Detail View.
- **Immersive mode**: Viewer chrome is hidden. This one is a viewer state, not a selection mode.

## Interaction language

- **Open**: Navigate to a screen or view, or reveal a menu or dialog.
- **Select**: Add a folder or media item to the current multi-selection.
- **Pick**: Choose a single thing that is then committed, as in the Folder thumbnail picker, the
  Destination picker, or the **Place picker** and **Date picker**.
- **Pin** / **Unpin**: Move a folder to the top of Folders View, and undo that.
- **Favourite** / **Unfavourite**: Add or remove a media item from the system-backed Favourites
  collection. Never use these words for pinning folders.
- **Add to album**: Add media references to an Album without copying or moving the files.
- **Remove from album**: Remove media references from an Album without deleting the files.
- **Exclude** / **Unexclude**: Hide a folder from the gallery, and bring it back.
- **Copy to** / **Move to**: Duplicate or relocate media into a folder chosen in the Destination
  picker. A viewer-initiated move closes the viewer; a copy leaves it open.
- **Delete**: Permanently remove media or folders, including the confirmation dialog. Deleting an
  Album removes only the Album and its references, never its media.
- **Share**: Hand media to another app.
- **Rotate**: Turn a photo in the viewer. This is display-only and does not rewrite the file.
- **Use as background**: Hand an image to the system wallpaper cropper (images only, never GIFs
  or videos).
- **Zoom** and **Pan**: Change scale and position, either of a photo in the viewer or — by
  pinching a grid — of the column count.

When a request uses an approximate name, map it to the closest canonical term. Ask for
clarification only when multiple terms would lead to materially different behavior.
