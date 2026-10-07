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
**View switcher** in the top bar rather than by navigation. Folder Detail View is reached by
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
- **Settings screen** (`SettingsScreen`): App preferences, language, support, legal, and the
  tip jar.
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
  the View switcher where applicable, and the Overflow menu; in search mode the title is replaced
  by the **Search field**. Used by all five main views.
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
- **View switcher**: The interactive browse-top-bar title and trailing chevron that open a menu of
  Folders View, Timeline View, and Albums View. The current view is marked with a check.
- **Search button** / **Search field**: Open live name filtering, and the field it filters from.
  Search is per view and deliberately not persisted.

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
- **Favourites** (`FavouritesAlbumGridItem`, `FavouritesAlbumListItem`): The fixed, system-backed
  collection of media marked with `MediaStore.IS_FAVORITE`. It cannot be created, renamed, or
  deleted, and is unavailable on API 28–29.

> **Favourite is not Pinned.** **Favourite / Unfavourite** marks individual media items and
> controls Favourites. **Pin / Unpin** only changes the ordering of folders in Folders View. Never
> use either word for the other operation.

## Album Detail View

- **Favourite heart**: The filled or outlined heart that favourites or unfavourites a media item.
  It is available in the Viewer action row and media-selection overflow menus when Favourites is
  supported. Aliases: **heart**, **favourite button**
- **Hidden-members notice** (tagged `album_hidden_items_notice`): The informational notice shown
  when an Album has stored members that cannot currently be resolved against `MediaStore`, such as
  after restore or while an SD card is absent. It means those references are retained and waiting;
  it is never an error, proof of deletion, or data loss. A fully unresolved Album may therefore
  appear empty while this notice is shown.
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
- **Add excluded folder dialog** (`AddExcludedFolderDialog`): Excludes a folder by browsing to it
  from the Manage excluded screen.
- **Create album dialog** (`CreateAlbumDialog`): Names a new Album.
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
- **Pick**: Choose a single thing that is then committed, as in the Folder thumbnail picker or
  the Destination picker.
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
