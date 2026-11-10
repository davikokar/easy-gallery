#!/usr/bin/env python3
"""Builds the Easy Gallery gazetteer assets from GeoNames dumps (ADR-0019 Amendment 1).

Run by hand, never by Gradle. Python 3, standard library only.

Usage (from the repository root):

    1. Download these three files into tools/gazetteer/raw/ (gitignored) with normal
       certificate verification, from https://download.geonames.org/export/dump/ :
           cities15000.zip   admin1CodesASCII.txt   countryInfo.txt
    2. python tools/gazetteer/build_gazetteer.py
       Writes app/src/main/assets/gazetteer/{places,admin1,countries}.tsv.
       Options: --raw-dir DIR, --out-dir DIR, --download-date YYYY-MM-DD.
    3. python tools/gazetteer/build_gazetteer.py --check
       Re-validates the committed assets without needing the raw files.

Assets (UTF-8, LF, tab separated, `.` decimal separator; lines starting with `#` are the header):
    places.tsv     latitude, longitude, name, country code, admin1 code
    admin1.tsv     "<country code>.<admin1 code>", name   (only keys some written place references)
    countries.tsv  ISO 3166 alpha-2 code, English name

Rules:
    - places.tsv keeps every cities15000 row except feature codes PPLH, PPLQ, PPLW, PPLCH.
    - Rows are written in ascending geonameid order (the id column is then dropped), so the order is
      stable across regenerations of the same input; the lookup's tie-break depends on it.
    - Names are GeoNames' `name` column, not `asciiname`.
    - An admin1 code that admin1CodesASCII.txt does not define is blanked in places.tsv (the place
      and country stay; the region is then null, exactly as for `00`). The header says how many.
    - Malformed input, or a name that cannot be written as one TSV field, aborts before any file
      is written.

Dataset version: DATASET_VERSION below is the one integer the Kotlin side compares with
PLACE_RESOLVER_VERSION. Bump both together when the dataset, the cap or the algorithm changes.
"""

import argparse
import datetime
import hashlib
import os
import re
import sys
import zipfile

DATASET_VERSION = 1

SOURCE_ZIP = "cities15000.zip"
SOURCE_ZIP_MEMBER = "cities15000.txt"
SOURCE_ADMIN1 = "admin1CodesASCII.txt"
SOURCE_COUNTRIES = "countryInfo.txt"
SOURCE_FILES = (SOURCE_ZIP, SOURCE_ADMIN1, SOURCE_COUNTRIES)

EXCLUDED_FEATURE_CODES = ("PPLH", "PPLQ", "PPLW", "PPLCH")
PLACES_COLUMN_COUNT = 19
NO_ADMIN1 = "00"

LICENCE = (
    "Data from GeoNames (https://www.geonames.org/), licensed under Creative Commons "
    "Attribution 4.0 (https://creativecommons.org/licenses/by/4.0/). "
    "Modified: trimmed to populated places and to the fields the app uses, "
    "with historical and abandoned places removed."
)

REPO_ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DEFAULT_RAW_DIR = os.path.join(REPO_ROOT, "tools", "gazetteer", "raw")
DEFAULT_OUT_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "gazetteer")

COORDINATE = re.compile(r"^-?\d{1,3}(\.\d+)?$")
COUNTRY_CODE = re.compile(r"^[A-Z]{2}$")
ADMIN1_CODE = re.compile(r"^[A-Za-z0-9]{1,8}$")
ADMIN1_KEY = re.compile(r"^[A-Z]{2}\.[A-Za-z0-9]{1,8}$")


class GazetteerError(Exception):
    pass


def fail(message):
    raise GazetteerError(message)


def sha256_and_size(path):
    digest = hashlib.sha256()
    size = 0
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
            size += len(block)
    return digest.hexdigest(), size


def read_text_lines(raw_bytes, label):
    try:
        text = raw_bytes.decode("utf-8")
    except UnicodeDecodeError as error:
        fail("%s is not valid UTF-8: %s" % (label, error))
    # Split on \n only: str.splitlines() would also break on U+2028 and friends inside names.
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
    return [line[:-1] if line.endswith("\r") else line for line in lines]


def clean_name(value, label, line_number):
    # A tab or line break would shift every later column; fail rather than rewrite a name.
    # Edge whitespace is trimmed (countryInfo.txt has "Bonaire, Saint Eustatius and Saba ").
    for character in value:
        if ord(character) < 32 or ord(character) == 127 or character in "\u0085\u2028\u2029":
            fail("%s line %d: control character U+%04X in %r" % (label, line_number, ord(character), value))
    value = value.strip()
    if not value:
        fail("%s line %d: empty name" % (label, line_number))
    return value


def parse_countries(path):
    with open(path, "rb") as handle:
        lines = read_text_lines(handle.read(), SOURCE_COUNTRIES)
    countries = {}
    for number, line in enumerate(lines, start=1):
        if not line.strip() or line.startswith("#"):
            continue
        columns = line.split("\t")
        if len(columns) < 9:
            fail("%s line %d: %d columns, expected at least 9" % (SOURCE_COUNTRIES, number, len(columns)))
        code, name, continent = columns[0], columns[4], columns[8]
        if not COUNTRY_CODE.match(code):
            fail("%s line %d: bad country code %r" % (SOURCE_COUNTRIES, number, code))
        name = clean_name(name, SOURCE_COUNTRIES, number)
        if code in countries:
            fail("%s line %d: duplicate country code %s" % (SOURCE_COUNTRIES, number, code))
        countries[code] = (name, continent)
    if len(countries) < 200:
        fail("%s: only %d countries read, the file is truncated or changed layout" % (SOURCE_COUNTRIES, len(countries)))
    return countries


def parse_admin1(path):
    with open(path, "rb") as handle:
        lines = read_text_lines(handle.read(), SOURCE_ADMIN1)
    admin1 = {}
    for number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        columns = line.split("\t")
        if len(columns) != 4:
            fail("%s line %d: %d columns, expected 4" % (SOURCE_ADMIN1, number, len(columns)))
        key, name = columns[0], columns[1]
        if not ADMIN1_KEY.match(key):
            fail("%s line %d: bad admin1 key %r" % (SOURCE_ADMIN1, number, key))
        name = clean_name(name, SOURCE_ADMIN1, number)
        if key in admin1:
            fail("%s line %d: duplicate admin1 key %s" % (SOURCE_ADMIN1, number, key))
        admin1[key] = name
    if len(admin1) < 1000:
        fail("%s: only %d keys read, the file is truncated or changed layout" % (SOURCE_ADMIN1, len(admin1)))
    return admin1


def parse_places(path):
    with zipfile.ZipFile(path) as archive:
        if archive.namelist() != [SOURCE_ZIP_MEMBER]:
            fail("%s: expected exactly one member %s, found %r" % (SOURCE_ZIP, SOURCE_ZIP_MEMBER, archive.namelist()))
        lines = read_text_lines(archive.read(SOURCE_ZIP_MEMBER), SOURCE_ZIP_MEMBER)
    rows = []
    seen_ids = set()
    for number, line in enumerate(lines, start=1):
        columns = line.split("\t")
        if len(columns) != PLACES_COLUMN_COUNT:
            fail("%s line %d: %d columns, expected %d" % (SOURCE_ZIP_MEMBER, number, len(columns), PLACES_COLUMN_COUNT))
        if not columns[0].isdigit():
            fail("%s line %d: bad geonameid %r" % (SOURCE_ZIP_MEMBER, number, columns[0]))
        geoname_id = int(columns[0])
        if geoname_id in seen_ids:
            fail("%s line %d: duplicate geonameid %d" % (SOURCE_ZIP_MEMBER, number, geoname_id))
        seen_ids.add(geoname_id)
        name, latitude, longitude = columns[1], columns[4], columns[5]
        feature_code, country, admin1 = columns[7], columns[8], columns[10]
        name = clean_name(name, SOURCE_ZIP_MEMBER, number)
        for text, low, high, what in ((latitude, -90.0, 90.0, "latitude"), (longitude, -180.0, 180.0, "longitude")):
            if not COORDINATE.match(text) or not low <= float(text) <= high:
                fail("%s line %d: bad %s %r" % (SOURCE_ZIP_MEMBER, number, what, text))
        if not COUNTRY_CODE.match(country):
            fail("%s line %d: bad country code %r" % (SOURCE_ZIP_MEMBER, number, country))
        if admin1 and not ADMIN1_CODE.match(admin1):
            fail("%s line %d: bad admin1 code %r" % (SOURCE_ZIP_MEMBER, number, admin1))
        if not feature_code:
            fail("%s line %d: empty feature code" % (SOURCE_ZIP_MEMBER, number))
        rows.append((geoname_id, name, latitude, longitude, feature_code, country, admin1))
    if len(rows) < 20000:
        fail("%s: only %d rows read, the file is truncated or changed layout" % (SOURCE_ZIP_MEMBER, len(rows)))
    return rows


def header_lines(asset_name, columns_description, inputs, download_date, counts):
    lines = [
        "# Easy Gallery gazetteer asset: %s" % asset_name,
        "# dataset-version: %d" % DATASET_VERSION,
        "# generated-by: tools/gazetteer/build_gazetteer.py",
        "# download-date-utc: %s" % download_date,
    ]
    for name, (digest, size) in inputs:
        lines.append("# source: %s sha256=%s bytes=%d" % (name, digest, size))
    for key, value in counts:
        lines.append("# %s: %s" % (key, value))
    lines.append("# columns: %s" % columns_description)
    lines.append("# license: %s" % LICENCE)
    return lines


def build_assets(raw_dir, download_date):
    paths = {name: os.path.join(raw_dir, name) for name in SOURCE_FILES}
    for name, path in paths.items():
        if not os.path.isfile(path):
            fail("missing input %s (download it from https://download.geonames.org/export/dump/)" % path)
    inputs = [(name, sha256_and_size(paths[name])) for name in SOURCE_FILES]

    countries = parse_countries(paths[SOURCE_COUNTRIES])
    admin1 = parse_admin1(paths[SOURCE_ADMIN1])
    rows = parse_places(paths[SOURCE_ZIP])

    rows.sort(key=lambda row: row[0])
    dropped = {}
    undefined_admin1 = {}
    kept = []
    for _, name, latitude, longitude, feature_code, country, admin1_code in rows:
        if feature_code in EXCLUDED_FEATURE_CODES:
            dropped[feature_code] = dropped.get(feature_code, 0) + 1
            continue
        if country not in countries:
            fail("place %r references country %s, which %s does not define" % (name, country, SOURCE_COUNTRIES))
        if admin1_code and admin1_code != NO_ADMIN1 and "%s.%s" % (country, admin1_code) not in admin1:
            key = "%s.%s" % (country, admin1_code)
            undefined_admin1[key] = undefined_admin1.get(key, 0) + 1
            admin1_code = ""
        kept.append((latitude, longitude, name, country, admin1_code))
    if not kept:
        fail("the row filter removed every place")

    referenced = sorted(
        {"%s.%s" % (row[3], row[4]) for row in kept if row[4] and row[4] != NO_ADMIN1}
    )
    dropped_text = ", ".join("%s %d" % item for item in sorted(dropped.items())) or "none"
    undefined_text = ", ".join("%s x%d" % item for item in sorted(undefined_admin1.items())) or "none"

    places_header = header_lines(
        "places.tsv",
        "latitude<TAB>longitude<TAB>name<TAB>country-code<TAB>admin1-code",
        inputs,
        download_date,
        [
            ("rows-read", len(rows)),
            ("rows-dropped-by-filter", "%d (%s)" % (sum(dropped.values()), dropped_text)),
            ("admin1-undefined-blanked", "%d rows (%s)" % (sum(undefined_admin1.values()), undefined_text)),
            ("rows-written", len(kept)),
        ],
    )
    admin1_header = header_lines(
        "admin1.tsv",
        "country-code.admin1-code<TAB>name",
        inputs,
        download_date,
        [
            ("rows-read", len(admin1)),
            ("rows-dropped-unreferenced", len(admin1) - len(referenced)),
            ("rows-written", len(referenced)),
        ],
    )
    country_codes = sorted(countries)
    countries_header = header_lines(
        "countries.tsv",
        "country-code<TAB>english-name",
        inputs,
        download_date,
        [("rows-read", len(countries)), ("rows-written", len(country_codes))],
    )

    def render(header, data_rows):
        body = ["\t".join(row) for row in data_rows]
        return ("\n".join(header + body) + "\n").encode("utf-8")

    return {
        "places.tsv": render(places_header, kept),
        "admin1.tsv": render(admin1_header, [(key, admin1[key]) for key in referenced]),
        "countries.tsv": render(countries_header, [(code, countries[code][0]) for code in country_codes]),
    }


def validate_asset(label, data):
    """Returns (header dict, data rows). Fails on anything the Kotlin loader would reject."""
    if b"\r" in data:
        fail("%s: contains a carriage return" % label)
    lines = read_text_lines(data, label)
    header = {}
    rows = []
    for number, line in enumerate(lines, start=1):
        if line.startswith("#"):
            key, separator, value = line[1:].partition(":")
            if separator:
                header.setdefault(key.strip(), value.strip())
            continue
        rows.append(line.split("\t"))
    if header.get("dataset-version") != str(DATASET_VERSION):
        fail("%s: dataset-version is %r, expected %d" % (label, header.get("dataset-version"), DATASET_VERSION))
    if header.get("rows-written") != str(len(rows)):
        fail("%s: header says %s rows, found %d" % (label, header.get("rows-written"), len(rows)))
    if not rows:
        fail("%s: no data rows" % label)
    return header, rows


def validate_assets(assets):
    _, places = validate_asset("places.tsv", assets["places.tsv"])
    _, admin1_rows = validate_asset("admin1.tsv", assets["admin1.tsv"])
    _, country_rows = validate_asset("countries.tsv", assets["countries.tsv"])
    admin1 = {}
    for row in admin1_rows:
        if len(row) != 2 or not ADMIN1_KEY.match(row[0]) or not row[1] or row[0] in admin1:
            fail("admin1.tsv: bad row %r" % row)
        admin1[row[0]] = row[1]
    countries = {}
    for row in country_rows:
        if len(row) != 2 or not COUNTRY_CODE.match(row[0]) or not row[1] or row[0] in countries:
            fail("countries.tsv: bad row %r" % row)
        countries[row[0]] = row[1]
    referenced = set()
    for number, row in enumerate(places, start=1):
        if len(row) != 5:
            fail("places.tsv row %d: %d columns, expected 5" % (number, len(row)))
        latitude, longitude, name, country, code = row
        if not COORDINATE.match(latitude) or not -90.0 <= float(latitude) <= 90.0:
            fail("places.tsv row %d: bad latitude %r" % (number, latitude))
        if not COORDINATE.match(longitude) or not -180.0 <= float(longitude) <= 180.0:
            fail("places.tsv row %d: bad longitude %r" % (number, longitude))
        if not name or name != name.strip():
            fail("places.tsv row %d: bad name %r" % (number, name))
        if country not in countries:
            fail("places.tsv row %d: unknown country %r" % (number, country))
        if code and code != NO_ADMIN1:
            key = "%s.%s" % (country, code)
            if key not in admin1:
                fail("places.tsv row %d: admin1 key %s is not defined in admin1.tsv" % (number, key))
            referenced.add(key)
    if referenced != set(admin1):
        fail("admin1.tsv: %d keys are not referenced by any place" % len(set(admin1) - referenced))
    return len(places), len(admin1), len(countries)


def write_assets(assets, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    temporary = {}
    try:
        for name, data in assets.items():
            temporary[name] = os.path.join(out_dir, name + ".tmp")
            with open(temporary[name], "wb") as handle:
                handle.write(data)
        for name, tmp_path in temporary.items():
            os.replace(tmp_path, os.path.join(out_dir, name))
    finally:
        for tmp_path in temporary.values():
            if os.path.exists(tmp_path):
                os.remove(tmp_path)


def default_download_date(raw_dir):
    dates = [
        datetime.datetime.fromtimestamp(os.path.getmtime(os.path.join(raw_dir, name)), datetime.timezone.utc).date()
        for name in SOURCE_FILES
    ]
    return min(dates).isoformat()


def main():
    parser = argparse.ArgumentParser(description="Build the Easy Gallery gazetteer assets.")
    parser.add_argument("--raw-dir", default=DEFAULT_RAW_DIR)
    parser.add_argument("--out-dir", default=DEFAULT_OUT_DIR)
    parser.add_argument("--download-date", help="UTC date the inputs were fetched; defaults to the oldest input's modification date")
    parser.add_argument("--check", action="store_true", help="validate the existing assets in --out-dir and exit")
    args = parser.parse_args()

    try:
        if args.check:
            assets = {}
            for name in ("places.tsv", "admin1.tsv", "countries.tsv"):
                with open(os.path.join(args.out_dir, name), "rb") as handle:
                    assets[name] = handle.read()
            counts = validate_assets(assets)
            print("OK: %d places, %d admin1 names, %d countries" % counts)
            return 0

        download_date = args.download_date or default_download_date(args.raw_dir)
        datetime.date.fromisoformat(download_date)
        assets = build_assets(args.raw_dir, download_date)
        counts = validate_assets(assets)
        write_assets(assets, args.out_dir)
        for name, data in assets.items():
            print("wrote %s (%d bytes)" % (name, len(data)))
        print("OK: %d places, %d admin1 names, %d countries (dataset-version %d)" % (counts + (DATASET_VERSION,)))
        return 0
    except (GazetteerError, OSError, zipfile.BadZipFile) as error:
        print("ERROR: %s" % error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
