# ADR-0019: Offline reverse geocoding for region albums

- Status: Active
- Date: 2026-10-08
- Decision makers: Easy Gallery project maintainer

## Context

A region album has to answer "what country, region and place is this coordinate in" for an entire
photo library, **offline**. [albums.md](../../design/albums.md) §11 is unambiguous: no cloud, no
account, no network beyond the tip jar. [ADR-0018](0018-media-index-in-a-second-database.md)
supplies the coordinates; nothing yet supplies the names.

The starting concern was size. Measured on `download.geonames.org` on 2026-10-08:

| File | Published size | Contents |
|---|---|---|
| `allCountries.zip` | **402 MB** compressed | every feature GeoNames holds |
| `alternateNamesV2.zip` | **195 MB** compressed | names in other languages and scripts |
| `cities15000.zip` | **3.2 MB** compressed | "all cities with a population > 15000 or capitals (ca 25.000)" |
| `admin1CodesASCII.txt` | 148 KB | English display names for admin1 codes |
| `countryInfo.txt` | 31 KB | ISO codes, country names, and more |

The widely quoted figure of roughly 1.5 GB for GeoNames is for the **uncompressed**
`allCountries.txt`, not for the download. That uncompressed size was **not** verified here; see
*To be measured* below. What is verified is that the full dump is two orders of magnitude larger
than the subset this feature needs, and that the single largest avoidable component is the
alternate-names table.

Two existing decisions shape the answer. [ADR-0016 Amendment 1](0016-album-creation-type-choice-and-picker-screen.md)
already decided, for the manual picker, that **picking is navigation rather than search** — the app
shows the user the structure they already know instead of asking them to produce a string.
[ADR-0001](0001-on-demand-media-location-metadata.md) established how coordinates are read at all:
`androidx.exifinterface` for images, `MediaMetadataRetriever` for video, and
`MediaStore.setRequireOriginal` plus `ACCESS_MEDIA_LOCATION` on API 29+ to get unredacted values.

## Decision

### A. The user picks a place. They never type one.

The region picker offers **the countries the index actually found**, with photo counts, drilling
into regions and then into nearest places. The same applies to time: the picker offers the years
and months that have photos.

The app already knows the answer set — the index contains it. Asking the user to produce a string
and then matching that string against a dataset is strictly worse than showing them the answers,
and it is the same argument ADR-0016 Amendment 1 made for the manual picker: a user navigating
their own structure finds things by memory rather than by guessing at spelling.

**This is the decision that makes the rest affordable.** The dataset only ever has to answer
**location → name**. It never has to answer **name → location**. A searchable, multilingual,
alternate-name gazetteer exists to answer the second question, and it is the single largest part
of GeoNames — 195 MB compressed on its own. Not needing it is what removes most of the size.

Offering years and months rather than parsing typed dates has a second benefit: nothing is parsed,
so none of the locale traps that govern `MediaLocation.kt` and `AGENTS.md`'s formatting rules can
arise in the picker at all.

### B. Start with a trimmed GeoNames `cities15000` nearest-place table, shipped as an APK asset.

- **One artifact, one code path, three answers.** For a coordinate, find the nearest place in the
  table; its own row carries the place name, the admin1 code, and the country code. Country and
  region are read off the matched place rather than requiring a second dataset and a second lookup.
- Trimmed to **(latitude, longitude, name, country code, admin1 code)** from the 19-column dump.
  Everything else — `geonameid`, `alternatenames`, feature classes, population, elevation, DEM,
  timezone, modification date — is dropped.
- Display names for the codes come from `admin1CodesASCII.txt` and `countryInfo.txt`, which are
  small enough to ship whole.
- The resolve pass runs over the index's stored coordinates and writes the **derived** place
  columns ADR-0018 defines. It never opens a media file.

**Licence.** GeoNames' own `readme.txt` states the gazetteer extracts are licensed under
**Creative Commons Attribution 4.0** (verified 2026-10-08). Shipping the data therefore obliges a
visible attribution. The app has **no attribution surface today**: Settings has a *Legal* section
containing Privacy and Terms, both of which open external URLs, and an *About* dialog showing only
the app name and version. One must be added, in the same step that ships the dataset, with its
string in all ten locales.

## Alternatives Considered

**The platform `Geocoder`.** Zero bytes shipped, the best name quality available, and already
localised by the system. Rejected. The platform documents `Geocoder` as requiring a backend service
that is not part of the core framework, and gives no guarantee that the backend is on-device; on a
typical handset it is a network round trip, which breaks §11 outright. Beyond the rule, sending a
user's entire library of photo coordinates to a geocoding backend is exactly the posture this app
sells against, and it would be doing so in the background, without the user watching. It cannot
even be used opportunistically, because `Geocoder.isPresent()` can return false and the app would
then need the offline path anyway — at which point it is shipping both.

**Natural Earth admin-0 and admin-1 polygons, pre-rasterised into an indexed bitmap**, where each
pixel's palette index is a region ID. The lookup is O(1) arithmetic with no search structure at
all, an indexed PNG of region IDs compresses extremely well, and the licence is the best available:
naturalearthdata.com's terms state that all versions of the data are in the **public domain**, that
no permission is needed, and that crediting the authors is unnecessary (verified 2026-10-08) — so
it carries no attribution obligation whatsoever.

Not chosen as the starting point because it answers only "which polygon", so it yields a country
and a region but **no place name**, and a raster carries a few kilometres of error at coasts and
borders depending on the resolution chosen. It is **recorded here as the recommended upgrade path
for the country and region levels**, layered above the gazetteer rather than replacing it: the
nearest-place table keeps supplying names while the raster supplies an authoritative country. If
border misattribution proves visible in practice, that is the fix, and ADR-0018's captured/derived
split means adopting it costs a resolve pass rather than a re-index.

**OSM-derived extracts** — Nominatim data, Who's On First, or similar. Richer, free, and actively
maintained. Rejected on ODbL. The share-alike and database-right obligations that attach to a
derived database compiled into a shipped, closed-source APK are genuinely unclear, and the cost of
getting that wrong is out of all proportion to the benefit over a CC BY dataset that answers the
same question.

**Ship no dataset and label regions by coordinate.** Rejected as not being a feature. "Photos near
43.77, 11.25" is not a region album.

**Play Asset Delivery, or downloading the dataset on first use.** Rejected at this size. It adds a
delivery mode, an install-time failure case, and a first-run network dependency in order to save a
few megabytes — and a download would breach the spirit of §11 even though the data being fetched is
not personal.

## Consequences

### Positive

- Entirely offline, so §11 holds without qualification and no coordinate ever leaves the device.
- One artifact answers all three levels, so there is no second dataset to keep in sync with the
  first, and no case where the country and the place disagree.
- Browsing the places the user actually has needs no search index, no alternate names, and no
  multilingual name table — which is exactly the bulk that is not being shipped.
- ADR-0018's derived columns keep the choice reversible. A better gazetteer, or the Natural Earth
  raster, is a resolve pass over stored coordinates.
- Nothing is parsed from user-entered text, so the `Locale` traps recorded against ADR-0001 do not
  arise in the picker.

### Negative — and these are the ways to get it wrong

- **Download size.** Every user pays for the asset, including the majority who will never create a
  region album. The published `cities15000.zip` is 3.2 MB; a trimmed binary form should be
  materially smaller, but this ADR states no figure it has not measured.
- **A linear scan of the gazetteer per photo is the one certain way to make this slow.** Roughly
  25,000 candidates against tens of thousands of photos is of the order of a billion distance
  computations. A coarse grid bucket index over the gazetteer — bucket by whole degree, search the
  containing bucket and its neighbours — is a requirement, not an optimisation.
- **Nearest-place must be distance-capped.** Without a cap, a photo taken mid-ocean or in empty
  desert resolves to a city hundreds of kilometres away and the album is simply lying. Beyond the
  cap the place is null, and the country and region should be null too rather than guessed.
- **Nearest-place misattributes near borders.** A photo taken in Italy a few kilometres from the
  Swiss frontier may be nearest to a Swiss town and will then be filed under Switzerland, because
  the country is read off the matched place's own row. Accepted for now; the Natural Earth raster
  above is the recorded fix.
- **Most photos have no GPS at all.** Screenshots, downloads, and anything that has passed through
  a messaging app have had their EXIF stripped. Region albums will cover a minority of many
  libraries and possibly a small one. **The UI must say so** — how many of the library's items have
  a location — rather than presenting a short list as though it were everything. This is §6's "be
  honest about staleness" applied to coverage rather than to progress, and it is the single most
  likely way for this feature to read as broken while working exactly as designed.
- **`ACCESS_MEDIA_LOCATION` plus `setRequireOriginal` is mandatory on API 29+**, and the indexer
  must use that path rather than a plain URI. Confirmed against `data/MediaLocation.kt`, which
  already does precisely this for images from `Build.VERSION_CODES.Q` upwards. A plain URI returns
  coordinates redacted to absent, which is **indistinguishable from a photo that never had any** —
  so getting this wrong produces a feature that looks like it works and quietly covers nothing. The
  permission is already declared and is requested separately from the media-access gate; ADR-0001's
  warning against folding it into `MainActivity`'s `permissions.all { }` check still applies. It may
  also be denied, in which case region albums are empty and must say why.
- **Video location is a second code path** — `MediaMetadataRetriever` and ISO-6709 parsing, as
  ADR-0001 records. The indexer inherits both paths and both sets of failure modes.
- **The dataset is a snapshot and will age.** Places are renamed and administrative divisions are
  redrawn; the shipped copy only changes when the app ships. Acceptable for a label on a photo
  album, and the resolve pass makes refreshing it cheap.
- **Attribution becomes a permanent obligation.** CC BY must be honoured for as long as the data
  ships, and the attribution entry has to survive any future reorganisation of Settings.

### To be measured in step 2.6

This ADR deliberately states no number it has not verified. These are open and belong to
[step 2.6](../../design/albums-stage-2.md):

- The trimmed on-disk size of `cities15000` reduced to (latitude, longitude, name, country code,
  admin1 code) in whatever encoding is chosen, and the resulting APK size delta.
- The uncompressed size of `allCountries.txt`, if the comparison is to be quoted anywhere.
- Resolve-pass throughput on the SM-G990B, in rows per second, with the bucket index in place.
- The proportion of a realistic library that carries GPS at all, which decides whether region
  albums are a headline feature or a niche one — and therefore how prominent the coverage notice
  has to be.
- Whether the distance cap should be one global value or should scale with local place density.

## References

- [docs/design/albums.md](../../design/albums.md) §4 (region and time albums), §6 (the index, and
  staleness), §11 (no network); [docs/design/albums-stage-2.md](../../design/albums-stage-2.md)
  steps 2.6 and 2.7
- [ADR-0001](0001-on-demand-media-location-metadata.md) — how coordinates are read, and the
  permission asymmetry that must be preserved
- [ADR-0016](0016-album-creation-type-choice-and-picker-screen.md) Amendment 1 — browse, do not
  search; the argument this decision reuses
- [ADR-0018](0018-media-index-in-a-second-database.md) — the captured/derived split that makes this
  decision reversible
- [ADR-0017](0017-composable-album-rule-representation.md) — the `region` clause that consumes this
  vocabulary
- `app/src/main/java/com/davide/seddio/easygallery/data/MediaLocation.kt`,
  `ui/SettingsScreen.kt`, `app/src/main/AndroidManifest.xml`
- <https://download.geonames.org/export/dump/readme.txt> — file descriptions, column list, and the
  CC BY 4.0 statement
- <https://www.naturalearthdata.com/about/terms-of-use/> — the public-domain statement
- <https://developer.android.com/reference/android/location/Geocoder> — `isPresent()`, and the
  backend-service requirement
