---
description: Canonical names for Easy Gallery screens, views, areas, controls, and UI interactions.
applyTo: "app/src/**/ui/**/*.kt, app/src/**/MainActivity.kt"
---

# UI vocabulary

> **Maintenance — owner: Designer. Mode: in-change.**
> If you **add** a named screen, view, area, dialog, or control, name it here in the same change.
> You built it, you know it, and a vocabulary file that lags the UI misleads every agent it is
> injected into. **Renaming, removing, or resolving a collision between existing terms is the
> Designer's** — those need the whole vocabulary in view, not one entry. Album terms live in
> [docs/design/albums.md](../../docs/design/albums.md) until Stage 1 lands.

Use these canonical names when discussing, implementing, and testing Easy Gallery's UI.
Prefer the product-facing name in prose and include the Kotlin symbol when extra precision is
useful. Preserve these meanings when adding new UI terms.
Treat aliases as input synonyms only. Use the canonical bold name in responses, implementation
notes, and tests.

Several Kotlin symbols predate the canonical names and have not been renamed. Where that is so,
the symbol is given in parentheses and the mismatch is called out. Use the canonical name in
prose; use the real symbol in code.

## Main views

The app has **three main views**, carried by two screens.

- **Folders View** (`FolderListScreen` with `DisplayMode.GALLERY`): All device folders as a grid
  or list, each with a thumbnail and item count. The view the app opens on.
  Aliases: **gallery view**, **folder list**, **buckets**
- **Timeline View** (`FolderListScreen` with `DisplayMode.CALENDAR`, body drawn by
  `CalendarGrid`): Every image and video on the device, flat across folders and grouped under
  date headers. Aliases: **calendar view**, **all media**
- **Folder Detail View** (`FolderDetailScreen`): The images and videos inside one folder.
  Aliases: **folder contents**, **media grid**

Folders View and Timeline View are two modes of the same screen, switched by the **View toggle**
in the top bar rather than by navigation. Folder Detail View is reached by opening a folder.

> **Naming caveat:** `DisplayMode.GALLERY`/`CALENDAR`, `FolderListScreen`, and `CalendarGrid`
> predate these names. A rename to `DisplayMode.FOLDERS`/`TIMELINE`, `FoldersScreen`, and
> `TimelineGrid` is proposed but not done. Do not invent a third set of names in the meantime.

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
three main views above, **dialog** for modal content, and **area** for a region within a screen.

## Screen structure

Each main view is built as a pair:

- **Screen composable** (`FolderListScreen`, `FolderDetailScreen`): Reads the ViewModel and binds
  the view's `PreferenceScope`.
- **Content composable** (`FolderListContent`, `FolderDetailContent`): Stateless, takes plain
  values plus callbacks. This is what the instrumented tests drive, and every parameter is passed
  by name there, so adding or renaming one breaks those tests by design.

## Top bars

A view shows exactly one top bar at a time. The order it resolves in is given per view.

- **Browse top bar** (`SearchTopBar`): The default top bar. Shows the title, the Search button,
  the View toggle, and the Overflow menu; in search mode the title is replaced by the
  **Search field**. Used by all three main views.
- **Folder selection top bar** (`SelectionTopBar`): Shown while folders are multi-selected in
  Folders View. Carries the selection count and the folder actions.
- **Media selection top bar** (`MediaSelectionTopBar`): Shown while media items are
  multi-selected in Timeline View or Folder Detail View. Carries a close button, the selection
  count, and an Overflow menu holding **all** media actions.
- **Thumbnail picker top bar** (`ThumbnailPickerTopBar`): Shown while the Folder thumbnail picker
  is open in Folder Detail View. Takes precedence over both selection bars.

Shared parts:

- **Overflow menu**: The three-dot menu in a top bar. Qualify it by bar when needed, such as
  **media selection overflow menu**. Every item carries a leading icon and no tint.
- **Back button**: The top-bar control that returns to the previous destination.
- **View toggle**: The browse top bar control that switches between Folders View and Timeline View.
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

## Full-screen viewer

- **Pager**: The horizontal swipe between items in the current media list.
- **Viewer bottom bar**: The bar below the media, holding the Info overlay, the Video controls
  when the page is a video, and the Viewer action row. It occupies real layout space rather than
  covering the media, and the whole bar collapses in immersive mode.
- **Viewer action row**: Delete, Share, Rotate (images only), Info, and the **Viewer overflow
  menu** (Copy to, Move to, Use as background).
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
  `FOLDERS`, `TIMELINE`, or `FOLDER_DETAIL`. Each main view owns an independent set.
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
- **Thumbnail picker mode**: The folder cover is being chosen in Folder Detail View.
- **Immersive mode**: Viewer chrome is hidden. This one is a viewer state, not a selection mode.

## Interaction language

- **Open**: Navigate to a screen or view, or reveal a menu or dialog.
- **Select**: Add a folder or media item to the current multi-selection.
- **Pick**: Choose a single thing that is then committed, as in the Folder thumbnail picker or
  the Destination picker.
- **Pin** / **Unpin**: Move a folder to the top of Folders View, and undo that.
- **Exclude** / **Unexclude**: Hide a folder from the gallery, and bring it back.
- **Copy to** / **Move to**: Duplicate or relocate media into a folder chosen in the Destination
  picker. A viewer-initiated move closes the viewer; a copy leaves it open.
- **Delete**: Permanently remove media or folders, including the confirmation dialog.
- **Share**: Hand media to another app.
- **Rotate**: Turn a photo in the viewer. This is display-only and does not rewrite the file.
- **Use as background**: Hand an image to the system wallpaper cropper (images only, never GIFs
  or videos).
- **Zoom** and **Pan**: Change scale and position, either of a photo in the viewer or — by
  pinching a grid — of the column count.

When a request uses an approximate name, map it to the closest canonical term. Ask for
clarification only when multiple terms would lead to materially different behavior.
