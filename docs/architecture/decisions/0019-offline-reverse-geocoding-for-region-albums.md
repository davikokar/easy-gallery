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

  > **Amended 2026-11-13 — see Amendment 3 below.** The figure is said in the Settings screen's
  > Media index area only, not on the album or in the picker.
- **`ACCESS_MEDIA_LOCATION` plus `setRequireOriginal` is mandatory on API 29+**, and the indexer
  must use that path rather than a plain URI. Confirmed against `data/MediaLocation.kt`, which
  already does precisely this for images from `Build.VERSION_CODES.Q` upwards. A plain URI returns
  coordinates redacted to absent, which is **indistinguishable from a photo that never had any** —
  so getting this wrong produces a feature that looks like it works and quietly covers nothing. The
  permission is already declared and is requested separately from the media-access gate; ADR-0001's
  warning against folding it into `MainActivity`'s `permissions.all { }` check still applies. It may
  also be denied, in which case region albums are empty and must say why.

  > **Amended 2026-11-13 — see Amendment 3 below.** The reason is said on the Settings screen's
  > location row, which shows that location access is off in place of a count, not on the album.
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

## Amendment 1 — 2026-11-09 — How the dataset is built and read, and the distance cap

> **Figures measured 2026-11-10.** The decisions below govern from 2026-11-09. M1–M5 were measured
> on 2026-11-10 and are recorded in *Measured* at the end, with device, date and method. Where a
> pre-measurement estimate in the text was wrong it is corrected in place and the estimate is left
> visible, marked as superseded.
>
> **The 100 km cap is kept, by maintainer decision on 2026-11-10, with known gaps.** M5 shows it
> failing this amendment's own test in sparse regions; the maintainer chose to keep it rather than
> raise it or densify the dataset — [albums.md](../../design/albums.md) §10 question 8 records the
> answer and why.

This amendment **adds** to the Decision and settles what the record left open. It reverses nothing:
a trimmed `cities15000` nearest-place table, offline, shipped as an APK asset, answering location →
name only, is still the decision. It is an amendment rather than a new ADR for the reason
[ADR-0018](0018-media-index-in-a-second-database.md) Amendment 1 gives — the reasoning that chose
the dataset did not change, and what follows is how that choice is built. It narrows two details,
each said where it happens: the display-name files are no longer shipped whole (§2), and the *To be
measured* list above is replaced by M1–M5 below.

The maintainer approved the five recommendations recorded here on 2026-11-09, when step 2.6 of
[albums-stage-2.md](../../design/albums-stage-2.md) was planned.

### 1. The dataset is generated by a checked-in script, run by hand

- A Python 3 script using **only the standard library** ~~downloads the GeoNames inputs,~~ filters
  and trims the GeoNames inputs, and writes the assets. It is **run by hand and never by Gradle.**
  The raw downloads are gitignored; the generated assets are committed. **Corrected 2026-11-10, as
  built:** the inputs are downloaded **by hand** into the gitignored `tools/gazetteer/raw/`; the
  script reads those local copies and never fetches anything, and its `--check` mode re-validates
  the committed assets. Plain TLS-verified downloads worked; never disable certificate checks to
  fetch them.
- **Why a script at all:** GeoNames publishes **no versioned dumps**. The files on
  `download.geonames.org` are replaced in place, so a dataset is reproducible only from a record of
  what was fetched and when. The script writes that record; a hand-trimmed file would have none.
- **Why not Gradle:** a build that downloads makes the APK's contents depend on the day it was
  built, and puts a network fetch into every clean build. Committing the output keeps the build
  hermetic and makes every dataset change a reviewable diff.
- **Why the standard library only:** no new dependency, so nothing for `AGENTS.md`'s
  ask-before-doing list, and it runs wherever Python 3 does.
- **Provenance travels in the asset.** Each asset opens with `#` header lines carrying the source
  file names, the download date (UTC), the **SHA-256 of each input file**, the row counts — read,
  dropped by the filter, written — the **resolver version** the asset is stamped with (§7), and the
  licence notice: GeoNames, CC BY 4.0 with its URL, and that the data was modified. Because there
  is no upstream version to cite, the header is the only answer to "which GeoNames ships in this
  APK".

### 2. The format is UTF-8 TSV

| Asset | Columns | Rows |
|---|---|---|
| `places.tsv` | latitude, longitude, name, country code, admin1 code | every kept place (§3) |
| `admin1.tsv` | country code + admin1 code, name | only the keys some kept place references |
| `countries.tsv` | ISO 3166 alpha-2 code, English name | from `countryInfo.txt` |

- **This narrows the Decision**, which said `admin1CodesASCII.txt` and `countryInfo.txt` are small
  enough to ship whole. They are, but whole they carry columns nothing reads and admin1 keys no
  shipped place can produce. Restricting `admin1.tsv` to referenced keys lets the script check that
  every key a place can yield is defined, instead of hoping so.
- Coordinates are written with a `.` decimal separator whatever the generating machine's locale,
  and parsed locale-independently — `AGENTS.md`'s rule for anything parsed back by machine.
- **Why not a prebuilt SQLite file:** it would be a **third database** beside the two
  [ADR-0009](0009-room-as-first-owned-datastore.md) and ADR-0018 govern, with its own open,
  singleton and versioning questions, for a table that is read once into memory and queried by
  nothing but the grid in §6.
- **Why not a binary encoding:** opaque in review and in `git diff`, a writer and a reader to keep
  in step, and a small saving — the APK compresses assets, and text of this shape compresses well.
  The pre-measurement estimate for the APK increase was ~~roughly 0.4–0.5 MB~~; **M1 measured
  +581,006 bytes (≈0.55 MiB)**, inside the budget, so TSV stays. If TSV breaks the budget, the format
  is revisited then, not pre-emptively.

### 3. The row filter

- Every `cities15000` row is kept **except** the feature codes `PPLH` (historical populated place),
  `PPLQ` (abandoned), `PPLW` (destroyed) and `PPLCH` (historical capital). A nearest-place answer
  names where a photo was taken; filing it under a place that no longer exists is wrong in a way the
  user sees and cannot correct.
- The script writes rows in ascending `geonameid` order before dropping that column, so the asset's
  row order is stable across regenerations of the same input. The tie-break in §6 depends on it.

### 4. The language of names

- **Place and region names are GeoNames' `name` form, not `asciiname`.** `asciiname` strips
  diacritics, which is wrong in every locale; `name` is the UTF-8 form GeoNames gives the place, and
  is overwhelmingly Latin script. Region names come from the `name` column of `admin1CodesASCII.txt`.
- **The accepted limit:** place and region names appear in that form in **all ten locales**, so in
  ar, ja, ko and zh-rCN a user reads Latin-script place names inside localised UI. Accepted because
  the alternative is a dataset step whose size is unmeasured, against a budget that is already
  spoken for.
- **Country names are localised at display time by the platform** — the display country of a
  region-only `Locale` built from the stored code (`Locale("", cc).getDisplayCountry(appLocale)` or
  its non-deprecated equivalent), in the app's language override, resolved at the point of use as
  `AGENTS.md` requires. Where the platform returns nothing usable — blank, or the code itself — the
  English name from `countries.tsv` is the fallback. The index stores codes, never country names,
  so a language switch needs no re-resolve.
- **Recorded as the upgrade path, not adopted:** localised place and region names from
  `alternateNamesV2`, filtered to the ten shipped languages and to the kept places. That file is the
  bulk Decision A exists to avoid; a filtered extract's size has not been measured, and adopting it
  would be a new decision on the size budget.

### 5. One global distance cap, provisionally 100 km

- **One global value, not one scaled to local place density** — the last question of the *To be
  measured* list, answered. A density-scaled cap needs a density model, which is a second dataset or
  a derived one, and turns "why does this photo have no place" into something no one can answer in a
  sentence. One number is testable and explainable. If M5 shows it fails, the remedy is a new
  decision, not tuning.
- **Provisionally 100 km, and the arithmetic behind it.** Earth's land is about 149 million km²,
  Antarctica included. Spread the ~~roughly 25,000~~ places GeoNames describes for `cities15000`
  evenly over it and each covers ~~about 6,000 km² — a square lattice about 77 km apart, on which no
  point of land is farther than about 55 km~~ (the spacing over √2) from a place. **Corrected
  2026-11-10:** the shipped dataset has **34,188** places, so each covers about 4,400 km² — a lattice
  about 66 km apart, and no point farther than about 47 km. The argument below is unchanged by it. Real places cluster where
  people live, so the nearest place is much closer than that in populated regions and much farther in
  empty ones. 100 km is roughly twice the even-spread worst case: wide enough that a photo taken
  anywhere people live should find a place, narrow enough that mid-ocean and deep-desert photos stay
  null rather than being filed under a city hundreds of kilometres away. **That is an argument, not a
  measurement.** M5 confirms the value or changes it before 2.6 closes. **M5's outcome, 2026-11-10:
  neither** — the value is kept with known gaps and the choice returned to the maintainer, who kept
  it the same day; *Measured* below has the figures and the verdict.
- Distance is **haversine on a sphere with R = 6371.0088 km**, the mean Earth radius. The sphere's
  error against the ellipsoid is a fraction of a percent — under a kilometre at the cap — which is
  far below anything the cap is meant to discriminate. A distance equal to the cap is within it.

### 6. The lookup: a 1° grid, a ring derived from the cap, proven by a linear scan

- Places are bucketed by whole degree: the row is the floor of the latitude, clamped so that 90°
  falls in the last row; the column is the floor of the longitude, modulo 360, so 180° and −180° are
  one column.
- **Latitude ring: ⌈cap / 111.2 km⌉ rows each way.** 111.2 km is one degree of latitude — πR/180
  at the R above, rounded. The code derives the degree from R rather than using the rounded figure,
  because a divisor rounded up narrows the ring. Rows clamp at the poles; they do not wrap.
- **Longitude ring: widened by 1/cos of the band's highest |latitude|** — the poleward cell edge of
  the rows being searched, not the query's own latitude — because a degree of longitude shrinks
  toward the pole and the cap reaches furthest in longitude at the band's poleward edge. The exact
  spherical bound is 2·asin(sin(δ/2) / cos φ) for δ = cap / R; its small-angle form δ / cos φ can
  fall short of it by a sliver that crosses a cell edge, so the code uses the exact form. **When the
  argument reaches 1, or the band touches a pole, the ring is every column.** Columns **wrap at the
  antimeridian**: a query at 179.9° searches the columns just east of −180°.
- **Proven by equivalence, not by argument.** A plain-JVM test runs the grid and a linear scan over
  **the real shipped asset** for a large deterministic set of query points — including the
  antimeridian, both poles, and points within the cap of a cell edge — and requires identical
  answers, null beyond the cap included. The linear scan is the definition and the grid is the
  optimisation. The scan stays a test oracle: the *Negative* consequence above that a scan per photo
  is the certain way to be slow still holds.
- **Ties are deterministic:** at equal distance, the row earlier in the asset wins.

### 7. What a resolve writes, and when it must refuse to

- **Within the cap, all three values come from the matched row** — the place name, the admin1 code
  and the country code. Never assembled from different rows; that is Decision B's "no case where
  the country and the place disagree".
- **Beyond the cap, all three are null.** Not the country alone, and not a nearest country. The
  *Negative* consequence above says null rather than guessed; this applies it to every level.
- **An admin1 code of `00` or empty gives a null region**, while the place and the country are
  still set. GeoNames uses `00` for features with no specific admin1 division. **Added as built,
  2026-11-10:** a code that `admin1CodesASCII.txt` does not define is blanked by the script and so
  reads the same way — 23 places in the shipped dataset — because a region code with no name
  is a label nobody can show.
- **A resolved row is marked as resolved**, so that "beyond the cap" is distinguishable from "not
  yet resolved". That marker, its column and its migration are
  [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 4. It holds `PLACE_RESOLVER_VERSION`,
  one constant: a regenerated dataset, a changed cap or a changed algorithm is a bump of it, and
  every row stamped with an older value is resolved again with no file I/O. The asset header carries
  the same number, and a unit test fails unless the two are equal — so a regenerated dataset cannot
  ship under an old version, and a cap or algorithm change cannot pass its tests until the header is
  re-stamped by the script.
- **The asset loads or fails loudly.** An asset that is empty, truncated — fewer rows than its
  header declares — or stamped with a different resolver version is rejected, and the resolve pass
  then stamps nothing. A pass that resolved against an empty table would mark every located row as
  beyond the cap, and nothing would ever retry them. "Indexes nothing and looks like it worked" is
  this stage's signature failure; this is the gazetteer's instance of it, closed before it is
  written.

### 8. Attribution

- **Where:** the Settings screen's Legal section gains a **Place data** row — a title, and a short
  credit subtitle naming GeoNames and CC BY 4.0. Tapping it opens a dialog with the full notice:
  the data comes from GeoNames, with its URL (<https://www.geonames.org/>); it is licensed under
  Creative Commons Attribution 4.0, with the licence URL
  (<https://creativecommons.org/licenses/by/4.0/>); it **was modified** — trimmed to populated places
  and to the fields the app uses, with historical and abandoned places removed; and GeoNames'
  disclaimer that the data is provided "as is" without warranty or any representation of accuracy,
  timeliness or completeness (`readme.txt`, re-read 2026-11-10).
- **As built, 2026-11-10:** the dialog says the data was modified — filtered and trimmed to the
  fields used, naming the three source files — but does not say that historical and abandoned
  places were removed. CC BY 4.0 asks that modification be indicated, which it is, so this is a
  narrower wording than this section asked for, not a licence breach. Recorded as a gap in
  [albums-stage-2.md](../../design/albums-stage-2.md) step 2.6.
- **Why there:** Legal is where Privacy and Terms already are and where a licence is looked for,
  and a named row is harder to lose in a reorganisation than a line inside the About dialog — the
  *Negative* consequence above that attribution must survive any future reorganisation of Settings.
- **Why the full text is in the app rather than behind a link:** a notice that can only be read
  online sits badly with albums.md §11. The URLs are shown as text.
- Its strings are in all ten locales. "GeoNames", the licence name and the URLs are not translated.
  The new row and dialog are added to the UI vocabulary in the change that builds them.

### 9. Natural Earth is unused in 2.6

It remains the recorded upgrade path for the country and region levels. Nothing in step 2.6 ships
it, so it adds no size and no attribution.

### To be measured — M1 to M5

This replaces the *To be measured* list above, which is left as written. Each figure is recorded
**here**, with its date, device and method, when it is measured.

- **M1 — size.** The bytes of each committed asset, and the **APK size delta**: the same build type
  built immediately before and after the assets land, compared file to file. Pre-measurement
  estimate: ~~roughly 0.4–0.5 MB~~; **measured +581,006 bytes (≈0.55 MiB)**, see *Measured* below.
  **Stop rule: if M1 is above 1 MB, 2.6 stops and returns to the
  maintainer** before any further work. The approved budget is an increase of up to 1 MB.
- **M2 — throughput.** Resolve-pass rows per second on the SM-G990B with the grid in place, from a
  log line around the pass.
- **M3 — coverage.** The share of live items with coordinates on the SM-G990B, which decides how
  prominent 2.7's coverage notice must be. Counts only; no coordinate is written into any document.
- **M4 — load cost.** Time to parse the assets and build the grid, and the heap the result retains,
  on the SM-G990B. It is paid once per worker run.
- **M5 — the cap.** Two halves. From the script: the distribution of each kept place's distance to
  its nearest neighbour — median, 95th and 99th percentile, and the share beyond the cap — which
  says how much land the cap leaves unlabelled by construction. On the device: the distribution of
  matched distances for the library's located rows, and the count beyond the cap, each beyond-cap
  row checked by hand for whether it was genuinely taken somewhere remote. **100 km is confirmed if
  no photo taken in a populated area lands beyond it**; otherwise the cap changes, here, with a bump
  of `PLACE_RESOLVER_VERSION`, before 2.6 closes.
- **Retired, not measured:** the uncompressed size of `allCountries.txt`. The list above made it
  conditional on being quoted somewhere; nothing quotes it, and the script never reads that file.

### Measured — 2026-11-10

Device figures are from a Samsung SM-G990B at API 36, through the device tests
`GazetteerDeviceTest` and `MediaIndexPlaceCoverageDeviceTest` and the resolve pass's log line;
script figures from `tools/gazetteer/measure_gazetteer.py` over the shipped assets. Each library
figure is **one library** and is not a rule.

**The dataset.** 34,188 places — 34,210 read, 22 dropped by §3's feature-code filter — with 2,763
referenced admin1 keys and 252 countries. The 23 places whose admin1 code GeoNames' own admin1
file does not define are blanked to a null region (§7). The Decision's *Negative* consequence and
§5 both said roughly 25,000; that described GeoNames' own label for the file, not the file as it
now is.

**M1 — size.** Release builds, unsigned, built 2026-11-10 immediately before and after the step:
5,786,850 bytes at `1162914`, 6,367,856 bytes with 2.6 — **+581,006 bytes, ≈0.55 MiB**. Of that,
`assets/gazetteer/*` is +554,403 compressed (places 527,864, admin1 23,935, countries 2,604;
1,232,077 bytes uncompressed), dex +25,640 and `resources.arsc` +9,820. **The 1 MB stop rule
holds.** The 0.4–0.5 MB estimate was low because the dataset is a third larger than assumed.

**M2 — throughput.** Two figures, because the one defined is not meaningful on this library:

- **The lookup:** 112,540 points per second over 50,000 synthetic points on the device — the grid
  alone, not the resolve pass.
- **The resolve pass as defined** — rows per second from its log line — on the device's real
  library: 18 rows in one chunk, about 650 ms in all, of which about 630 ms was the gazetteer load.
  **A per-row rate from 18 rows is noise around a fixed load cost**, so none is quoted. On a small
  library the pass's cost is M4, not M2.
- **Equivalence held**: bucketed equals linear on 3,000 points on the device and on 20,000 points
  over the real assets on the JVM, null beyond the cap included.

**M3 — coverage.** That device's library: 426 live items, **18 with coordinates (4.2%)**, all 18
resolved with a place. On this library region albums are a niche feature, which argues for a
prominent coverage notice; where it lives is step 2.7's open question.

**M4 — load cost.** Cold load 563 ms, warm 535 ms; about 1.54 MB retained in the primitive arrays,
the small interned string tables excluded. Paid once per worker run that finds a pending row; a run
with nothing pending does not load the assets.

**M5 — the cap.**

- **Script half** — each place's distance to its nearest other place: p50 10.5 km, p90 42.3,
  p95 62.2, p99 128.8; **1.85% of places have no other place within 100 km**. By continent, p95:
  EU 46.5, AS 50.7, NA 56.0, SA 73.3, AF 95.5, **OC 278.5**. Probes, as distance to the nearest
  place: Zermatt 34.4 km, Isle of Skye 89.7 and Yosemite Valley 90.9 — all inside; **east Iceland
  near Egilsstaðir 176.6 — outside**; mid-Pacific and the central Sahara outside, as intended.
- **Device half:** 0 of the 18 located rows lie beyond the cap, and 0 would at 25, 50, 100 or
  150 km. One small library; it cannot show the gaps the script half shows.
- **Verdict: kept by maintainer decision, 2026-11-10, with known gaps.** ~~Kept, with known gaps —
  not confirmed.~~ The test above is that no photo taken in a populated area lands beyond the cap,
  and it fails in sparse regions: east Iceland, much of Oceania, and the more than 1% of places that
  have no neighbour within it. The alternatives each cost something this amendment did not approve —
  raising the cap to about 180 km files wilderness photos under distant towns, and densifying to
  `cities5000` is likely to break the 1 MB budget — so the choice went to the maintainer, as
  [albums.md](../../design/albums.md) §10 question 8, who kept 100 km: a photo in a gap gets no
  place rather than a wrong one. The test's failure is accepted, not overturned.
- **A known cost if the cap changes.** The loader rejects an asset whose `dataset-version` header
  differs from `PLACE_RESOLVER_VERSION` (§7), so even a cap-only change means regenerating the
  assets. The script has no re-stamp mode, the raw downloads are gitignored, and GeoNames replaces
  its files in place — so regenerating today fetches newer data, not the data that shipped. A cap
  change therefore either accepts a dataset refresh with it, or first adds a re-stamp mode.

### Consequences

- **Positive.** The shipped dataset is identifiable from its own header and reproducible from it;
  every dataset change is a reviewable diff; the grid is proven against the definition rather than
  trusted; and re-resolution after any change to the data, the cap or the algorithm is one constant.
  **Qualified 2026-11-10:** reproducible only from the same input files, which the header identifies
  by SHA-256 but nothing keeps — see M5's known cost.
- **Negative.** Place and region names are Latin script in every locale (§4). The script depends on
  GeoNames' file layouts as they are on the day the inputs are downloaded, and only a person running
  it refreshes the data. The assets are parsed on every worker run, which M4 prices — about half a
  second on the SM-G990B, and only on a run that finds a pending row. And **GeoNames'
  admin1 codes are not stable across dataset versions** — its readme says most are FIPS codes,
  "subject to change to iso code" — so a regenerated dataset can change the region code a photo
  resolves to. Re-resolution keeps the index right; anything that stores a raw admin1 code elsewhere
  does not get that for free, which is a risk recorded against step 2.7.

### References

- [ADR-0018](0018-media-index-in-a-second-database.md) Amendment 4 — the resolver-version marker
  and the index's version 4
- [docs/design/albums-stage-2.md](../../design/albums-stage-2.md) step 2.6 — the approvals, the step
  list, and the exit criteria; [docs/design/albums.md](../../design/albums.md) §8 decision 16, §11
- `AGENTS.md` — *Strings and localisation* (machine-parsed values), *What to ask before doing*
- <https://download.geonames.org/export/dump/readme.txt> — the `name` and `asciiname` columns,
  `admin1CodesASCII.txt`'s columns, the admin1 `00` code and the FIPS-to-ISO note, the CC BY 4.0
  statement and the "as is" disclaimer (re-read 2026-11-10)
- <https://www.geonames.org/export/codes.html> — the feature codes the filter drops
- <https://creativecommons.org/licenses/by/4.0/> — the licence the attribution cites

## Amendment 2 — 2026-11-12 — The accepted limit covers the locales added since

The app added seven locales on 2026-11-12 — `tr`, `pl`, `ro`, `uk`, `vi`, `th` and Indonesian
(`id`) — and the shipped list is now `LocaleHelper.supportedLanguageTags`, not a number. This
amendment restates what that does to Amendment 1. **The decision stands unchanged**: place and
region names are still GeoNames' `name` form, country names are still localised by the platform,
and no dataset step is added.

- **The accepted limit (Amendment 1 §4) now also covers `uk` and `th`.** Ukrainian is written in
  Cyrillic and Thai in its own script, so those users, like `ar`, `ja`, `ko` and `zh-rCN` users,
  read Latin-script place and region names inside localised UI. Accepted for the same reason as
  before: the alternative is a dataset step of unmeasured size against a budget already spoken
  for. `tr`, `pl`, `ro`, `vi` and `id` are written in Latin script and add nothing new to the limit.
- **"All ten locales" in the Decision and in Amendment 1 §4 and §8 reads as every shipped locale.**
  The Place data row's strings are in every one, and `LocaleResourceParityTest` fails if any locale
  lacks them.
- **The upgrade path got more expensive, not cheaper.** Amendment 1 §4 recorded localised names
  from `alternateNamesV2`, filtered to the shipped languages, as the path not adopted. Each added
  language enlarges that filtered extract. Its size is still unmeasured, and adopting it is still a
  new decision on the size budget.

## Amendment 3 — 2026-11-13 — Coverage and the access-denied reason are said in Settings

The Decision and Amendments 1 and 2 stand unchanged. This amendment moves where two obligations in
*Consequences — Negative* are met; it does not drop either. **The maintainer decided it on
2026-11-13**; [albums.md](../../design/albums.md) §8 decision 29 records the decision, its
reasons, and its accepted cost.

Step 2.7 met both obligations on the album and in the picker, through the location coverage notice
of albums.md §8 decision 25. From this amendment they are met in the Settings screen's Media index
area only:

- **"The UI must say so."** A row reads "N items have no usable location information", counting
  live items indexed with no coordinates or resolved beyond the cap — the items decision 25's notice
  counted. It states the share without a location rather than the share with one, as the notice
  did; either answers the obligation, which is that a short list is not presented as everything.
- **"Must say why" when access is denied.** On API 29+ without `ACCESS_MEDIA_LOCATION`, that row
  shows the existing sentence saying location access is off, in place of the count. A count there
  would blame the photos for the app's missing access, because without the permission extraction
  stores no location at all.

### Consequences of this amendment

- **Negative.** A region album that is short or empty — for lack of GPS, for lack of the
  permission, or because the index has not reached its items — no longer says why on the album or
  in the picker. The user has to go to Settings for it. That is the "read as broken while working
  exactly as designed" risk named above, accepted by the maintainer rather than prevented.
- **Positive.** Each figure is in one place, beside the other index figures, and computed the same
  way they are.

### References added by this amendment

- [docs/design/albums.md](../../design/albums.md) §8 decisions 25 and 29, §10 question 9
- [ADR-0016](0016-album-creation-type-choice-and-picker-screen.md) Amendment 3 — the picker no
  longer shows the coverage notices
