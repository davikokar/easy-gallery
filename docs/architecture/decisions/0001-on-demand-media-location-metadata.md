# ADR-0001: On-demand media location metadata

- Status: Active
- Date: 2026-09-21
- Decision makers: Easy Gallery project maintainer

## Context

The app is adding a "GPS" row that shows decimal coordinates in its two single-item info
surfaces — the full-screen viewer overlay and `MediaPropertiesDialog` — with the coordinates
tappable to open a maps app.

Location is the first metadata field the app needs that is **not** available from a MediaStore
cursor column. Reading it requires opening the underlying file and parsing an EXIF header
(images/GIFs) or running a media extractor (videos). That cost is per item and non-trivial,
which puts it in direct tension with how the app loads media today:

- `MediaStoreDataSource.queryMedia` performs a single cursor walk, and `getAllMedia()` returns
  **every** image and video on the device to power Timeline View.
- There is no pagination anywhere in the media pipeline (a known, documented weakness).
- `MediaItem` is a flat value type populated entirely from cursor columns.
- `MediaRepository` is a small hand-implemented interface; the test double `FakeMediaRepository`
  is written by hand and shared by `GalleryViewModelTest` and `CreateFolderViewModelTest`.
- `GalleryViewModel` is already an oversized single state holder.
- On API 29+ the OS redacts location from media returned to apps unless the app holds
  `ACCESS_MEDIA_LOCATION` and explicitly asks for the unredacted original.
- The app ships an in-app language switcher (it/de/fr/es/pt-BR among others), so default-locale
  number formatting is not a safe assumption.

A decision was needed on **where** location is read, **who owns** it, and **how** the map is
launched, because each of those choices is expensive to reverse once a metadata row exists.

## Decision

Location metadata is read **lazily, per item, at the moment an info surface is displayed** —
never during MediaStore queries.

- **Images and GIFs**: `androidx.exifinterface` `ExifInterface.latLong`, using
  `MediaStore.setRequireOriginal(uri)` on API 29+ and the `ACCESS_MEDIA_LOCATION` runtime
  permission to obtain unredacted coordinates.
- **Videos**: `MediaMetadataRetriever.METADATA_KEY_LOCATION`, which returns an ISO-6709 string
  that is parsed into decimal degrees and requires no additional permission.
- **Ownership**: `MediaItem` is not extended with latitude/longitude, and `MediaRepository` gains
  no new method. The lookup lives in a new leaf data file, with a small Compose/intent glue file
  for display and map launching.
- **Map launching**: the app-neutral `geo:<lat>,<lng>?q=...` scheme, with an
  `https://www.google.com/maps` fallback and a final toast if neither resolves. Launches are
  wrapped in try/catch rather than gated on `Intent.resolveActivity`, so no `<queries>` manifest
  element is required.
- **Formatting**: all coordinate formatting is forced to `Locale.US`.
- **Sentinel handling**: coordinates of exactly `0.0, 0.0` are treated as absent.

`ACCESS_MEDIA_LOCATION` is requested **separately** from the existing media-access permission
gate, not folded into it.

## Alternatives Considered

**Eagerly populate `MediaItem.latitude`/`longitude` during `queryMedia` in
`MediaStoreDataSource`.** Rejected. `getAllMedia()` loads every image and video on the device for
Timeline View, so this converts a sub-second cursor walk into O(n) file opens plus EXIF or
media-extractor parses — minutes on a 10k-item device. It also compounds the already-documented
absence of pagination, since the cost is paid up front for items the user may never view.

**Add `getLocation()` to the `MediaRepository` interface.** Rejected. It breaks the shared
hand-written `FakeMediaRepository` used by `GalleryViewModelTest` and `CreateFolderViewModelTest`,
and it would require new state in the already-oversized `GalleryViewModel` — a substantial
architectural cost for a presentation-triggered leaf read with no reuse value elsewhere in the
app.

**Hardcode an `https://www.google.com/maps` URL instead of the `geo:` scheme.** Rejected. It
forces a single vendor, ignores the user's chosen default map application, and fails outright on
non-GMS / de-Googled devices. Google Maps is retained only as a fallback, not as the primary
target.

**Use the platform `android.media.ExifInterface` instead of the AndroidX artifact.** Rejected.
Its `getLatLong(): double[]` overload requires API 29 while the app's `minSdk` is 28, and it
supports a narrower set of formats. AndroidX handles HEIC, AVIF, WebP, DNG and RAW, which matter
for a modern camera gallery.

## Consequences

### Positive

- MediaStore query performance is unchanged; Timeline View's full-device load pays no location
  cost at all.
- Work is strictly proportional to what the user actually opens — at most one item at a time.
- `MediaItem`, `MediaRepository`, `FakeMediaRepository` and `GalleryViewModel` are untouched, so
  the existing unit test suite is unaffected.
- The `geo:` scheme respects the user's default map app and works on devices without Google
  Play services.
- No `<queries>` manifest element is needed, keeping the manifest and package-visibility surface
  minimal.
- Denial of `ACCESS_MEDIA_LOCATION` degrades gracefully: the GPS row is simply absent, and the
  rest of the app is unaffected.

### Negative

- Adds `ACCESS_MEDIA_LOCATION`, a `dangerous` permission whose entire purpose is de-redacting
  location. This carries Google Play Data Safety declaration implications even though the app
  never transmits location off-device.
- Adds `androidx.exifinterface` to a deliberately lean dependency set.
- Creates a permission-handling asymmetry that is easy to get wrong.
  `MainActivity.checkPermissions()` derives `hasPermission` from `permissions.all { granted }`;
  folding `ACCESS_MEDIA_LOCATION` into that array would make a denial of location blank the
  entire app behind `PermissionDeniedScreen`. The separate request path must be preserved by
  anyone touching that method.
- Because the lookup is Compose-scoped rather than ViewModel-cached, it re-reads on configuration
  change and on every pager swipe. Accepted: a single EXIF header read is on the order of
  milliseconds.
- Establishes a reusable precedent that presentation-triggered leaf reads may bypass
  `MediaRepository`. This is a deliberate narrowing of the repository's role and can be misapplied
  to reads that genuinely belong in the data layer.
- Any future location feature that needs **bulk** access — location-based grouping, a map view,
  "photos near me" — must revisit this ADR, because none of them can be served by a per-item
  on-display read.
- `Locale.US` formatting is mandatory rather than stylistic. Under the in-app language switcher
  (it/de/fr/es/pt-BR), default-locale `%.6f` produces a comma decimal separator, which would
  corrupt the comma-delimited `geo:` URI. This constraint is invisible in the default English
  build and will not be caught by an English-only test pass.
- Treating `0.0, 0.0` as absent means genuine Null Island media cannot display coordinates. This
  is accepted because EXIF defaults and buggy encoders emit that value frequently enough that the
  false-positive rate would otherwise be worse.
- Video location depends on `MediaMetadataRetriever` and ISO-6709 parsing, a different code path
  and different failure modes from the image path — two implementations to maintain for one
  user-facing row.

## References

- `app/src/main/java/com/davide/seddio/easygallery/MainActivity.kt` — `checkPermissions()`, the
  `permissions.all { }` gate that must not absorb `ACCESS_MEDIA_LOCATION`
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaStoreDataSource.kt` — `queryMedia`,
  `getAllMedia()`
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaRepository.kt`
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaItem.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/components/MediaPropertiesDialog.kt`
- `app/src/main/java/com/davide/seddio/easygallery/ui/FullImageScreen.kt`
- `app/src/test/java/com/davide/seddio/easygallery/data/FakeMediaRepository.kt`
- `androidx.exifinterface` `ExifInterface.latLong`
- `MediaStore.setRequireOriginal(Uri)` and `Manifest.permission.ACCESS_MEDIA_LOCATION`
- `MediaMetadataRetriever.METADATA_KEY_LOCATION` (ISO-6709)
