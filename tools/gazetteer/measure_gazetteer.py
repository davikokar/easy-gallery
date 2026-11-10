#!/usr/bin/env python3
"""Script half of ADR-0019 Amendment 1's M1 and M5. Python 3, standard library only.

Usage (from the repository root, after build_gazetteer.py has run):

    python tools/gazetteer/measure_gazetteer.py

Reads the committed assets and tools/gazetteer/raw/countryInfo.txt (for the continent column) and
prints: asset sizes, raw and deflated; nearest-neighbour distance percentiles between kept places,
globally and per continent; and the nearest place to a fixed list of probe points.

Distance is haversine on a sphere, R = 6371.0088 km, as the amendment specifies. Percentiles use
the nearest-rank method. The nearest-neighbour search is exact: places are sorted by latitude and
each scan stops once the latitude gap alone exceeds the best distance found.
"""

import bisect
import math
import os
import sys
import zlib

REPO_ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ASSET_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "gazetteer")
COUNTRY_INFO = os.path.join(REPO_ROOT, "tools", "gazetteer", "raw", "countryInfo.txt")

EARTH_RADIUS_KM = 6371.0088
CAP_KM = 100.0
PERCENTILES = (50, 90, 95, 99)

PROBES = (
    ("Yosemite Valley", 37.7456, -119.5936),
    ("Isle of Skye", 57.3000, -6.3000),
    ("East Iceland (Egilsstadir)", 65.2669, -14.3948),
    ("Zermatt", 46.0207, 7.7491),
    ("Mid-Pacific", -30.0, -140.0),
    ("Central Sahara", 23.0, 12.0),
)


def haversine_km(lat1, lon1, lat2, lon2):
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = phi2 - phi1
    d_lambda = math.radians(lon2 - lon1)
    a = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(min(1.0, math.sqrt(a)))


def read_rows(path):
    with open(path, "r", encoding="utf-8", newline="") as handle:
        return [line.rstrip("\n").split("\t") for line in handle if not line.startswith("#") and line.strip()]


def percentile(sorted_values, p):
    rank = max(1, math.ceil(p / 100.0 * len(sorted_values)))
    return sorted_values[rank - 1]


def nearest_index(places, latitudes, lat, lon, skip_index=None):
    """Exact nearest place (distance_km, index). `places` is sorted by latitude."""
    start = bisect.bisect_left(latitudes, lat)
    best, best_index = math.inf, None
    km_per_degree = math.radians(1.0) * EARTH_RADIUS_KM
    for step, bound in ((1, len(places)), (-1, -1)):
        i = start if step == 1 else start - 1
        while i != bound:
            if (abs(latitudes[i] - lat) * km_per_degree) > best:
                break
            if i != skip_index:
                d = haversine_km(lat, lon, places[i][0], places[i][1])
                # Lower index wins a tie, matching the asset-order tie-break of ADR-0019 §6.
                if d < best or (d == best and best_index is not None and places[i][4] < places[best_index][4]):
                    best, best_index = d, i
            i += step
    return best, best_index


def main():
    for name in ("places.tsv", "admin1.tsv", "countries.tsv"):
        with open(os.path.join(ASSET_DIR, name), "rb") as handle:
            data = handle.read()
        print("%-14s %9d bytes raw  %8d bytes deflate(9)" % (name, len(data), len(zlib.compress(data, 9))))

    continents = {}
    with open(COUNTRY_INFO, "r", encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("#") or not line.strip():
                continue
            columns = line.rstrip("\r\n").split("\t")
            continents[columns[0]] = columns[8]

    raw = read_rows(os.path.join(ASSET_DIR, "places.tsv"))
    country_names = dict(read_rows(os.path.join(ASSET_DIR, "countries.tsv")))
    # (lat, lon, name, country, asset row number); row number carries the asset-order tie-break.
    places = sorted(
        ((float(r[0]), float(r[1]), r[2], r[3], n) for n, r in enumerate(raw)),
        key=lambda p: (p[0], p[4]),
    )
    latitudes = [p[0] for p in places]
    print("\nplaces: %d" % len(places))

    by_group = {"GLOBAL": []}
    for i, place in enumerate(places):
        distance, _ = nearest_index(places, latitudes, place[0], place[1], skip_index=i)
        continent = continents.get(place[3])
        if continent is None:
            print("ERROR: country %s has no continent in countryInfo.txt" % place[3], file=sys.stderr)
            return 1
        by_group["GLOBAL"].append(distance)
        by_group.setdefault(continent, []).append(distance)

    print("\nNearest-neighbour distance between kept places, km (nearest-rank percentiles)")
    print("%-8s %6s %8s %8s %8s %8s %8s %8s %9s %8s" % ("group", "n", "p50", "p90", "p95", "p99", "max", "zero", ">cap", ">cap %"))
    order = ["GLOBAL"] + sorted(k for k in by_group if k != "GLOBAL")
    for key in order:
        values = sorted(by_group[key])
        beyond = sum(1 for v in values if v > CAP_KM)
        zero = sum(1 for v in values if v == 0.0)
        print(
            "%-8s %6d %8.1f %8.1f %8.1f %8.1f %8.1f %8d %9d %7.2f%%"
            % (key, len(values), *(percentile(values, p) for p in PERCENTILES), values[-1], zero, beyond, 100.0 * beyond / len(values))
        )

    print("\nProbe points: nearest kept place (cap %.0f km)" % CAP_KM)
    print("%-28s %-18s %-26s %-18s %9s %s" % ("probe", "lat,lon", "nearest place", "country", "km", "within cap"))
    for name, lat, lon in PROBES:
        distance, index = nearest_index(places, latitudes, lat, lon)
        place = places[index]
        print(
            "%-28s %-18s %-26s %-18s %9.1f %s"
            % (name, "%.4f,%.4f" % (lat, lon), place[2], country_names.get(place[3], place[3]), distance, "yes" if distance <= CAP_KM else "NO")
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
