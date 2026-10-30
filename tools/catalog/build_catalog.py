#!/usr/bin/env python3
"""Builds the BestFlightPrice catalog (stdlib only). Raw downloads live in .local/raw (gitignored).

Outputs (committed):
  data/catalog/airports.json, destinations.json, countries.json   -> bundled in the apps
  data/server/climate.json, cyclones.json, seismic.json, experiences.json, manifest.json -> server only
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import sys
import time
import urllib.request
import zipfile
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / ".local" / "raw"
CURATED = Path(__file__).resolve().parent / "curated"
OUT_CATALOG = ROOT / "data" / "catalog"
OUT_SERVER = ROOT / "data" / "server"

CYCLONE_RADIUS_KM = 300.0
STORM_MIN_KT = 34.0
FIRST_YEAR, LAST_YEAR = 1980, 2025
MODERATE_RATE, HIGH_RATE = 0.05, 0.20
MIN_CITIES_PER_COUNTRY = 1

# Remote places with no populated place within 400 km (IANA zone names).
TZ_OVERRIDES = {"ec-galapagos": "Pacific/Galapagos", "pf-tahiti": "Pacific/Tahiti", "fj-nadi": "Pacific/Fiji"}

MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]


# ---------- pure helpers (unit tested) ----------
def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def classify_cyclone(storms_per_year: float) -> str:
    if storms_per_year >= HIGH_RATE:
        return "HIGH"
    if storms_per_year >= MODERATE_RATE:
        return "MODERATE"
    return "LOW"


def parse_months(s: str) -> list[int]:
    return sorted({int(x) for x in s.split(",") if x.strip()})


def parse_pipe_file(path: Path) -> list[list[str]]:
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        rows.append([c.strip() for c in line.split("|")])
    return rows


def parse_experiences(path: Path) -> list[dict]:
    out = []
    for r in parse_pipe_file(path):
        eid, dests, peak, shoulder, basis = r
        out.append({
            "id": eid,
            "destinations": [d.strip() for d in dests.split(",")],
            "peak": parse_months(peak),
            "shoulder": parse_months(shoulder),
            "basis": basis,
        })
    return out


def nearest_timezone(lat: float, lon: float, places: list[tuple[float, float, str]], max_km: float = 150.0) -> str | None:
    best, best_d = None, max_km
    for plat, plon, tz in places:
        if abs(plat - lat) > max_km / 111.0 + 0.5:
            continue
        d = haversine_km(lat, lon, plat, plon)
        if d < best_d:
            best, best_d = tz, d
    return best


def stable_dump(obj, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# ---------- fetching ----------
SOURCES = {
    "airports.csv": "https://davidmegginson.github.io/ourairports-data/airports.csv",
    "geonames.zip": "https://download.geonames.org/export/dump/cities15000.zip",
    "ibtracs.csv": "https://www.ncei.noaa.gov/data/international-best-track-archive-for-climate-stewardship-ibtracs/v04r01/access/csv/ibtracs.since1980.list.v04r01.csv",
}


def fetch(url: str, dest: Path, retries: int = 3) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 0:
        return
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "best-flight-prices-catalog/1.0"})
            with urllib.request.urlopen(req, timeout=120) as r, dest.open("wb") as f:
                while chunk := r.read(1 << 20):
                    f.write(chunk)
            return
        except Exception as e:  # noqa: BLE001
            if attempt == retries - 1:
                raise
            print(f"retry {url}: {e}", file=sys.stderr)
            time.sleep(2 * (attempt + 1))


def fetch_json_cached(url: str, cache: Path):
    fetch(url, cache)
    return json.loads(cache.read_text(encoding="utf-8"))


# ---------- builders ----------
def load_airports() -> dict[str, dict]:
    fetch(SOURCES["airports.csv"], RAW / "airports.csv")
    out = {}
    with (RAW / "airports.csv").open(encoding="utf-8", newline="") as f:
        for r in csv.DictReader(f):
            iata = r["iata_code"].strip()
            if not iata:
                continue
            out[iata] = {
                "iata": iata, "name": r["name"], "city": r["municipality"], "country": r["iso_country"],
                "lat": round(float(r["latitude_deg"]), 4), "lon": round(float(r["longitude_deg"]), 4),
                "type": r["type"], "scheduled": r["scheduled_service"] == "yes",
            }
    return out


def load_timezone_places() -> list[tuple[float, float, str]]:
    fetch(SOURCES["geonames.zip"], RAW / "geonames.zip")
    places = []
    with zipfile.ZipFile(RAW / "geonames.zip") as z:
        with z.open("cities15000.txt") as f:
            for line in f.read().decode("utf-8").splitlines():
                c = line.split("\t")
                places.append((float(c[4]), float(c[5]), c[17]))
    return places


def build_airports(all_airports: dict[str, dict], needed: set[str]) -> list[dict]:
    rows = []
    for a in all_airports.values():
        keep = (a["type"] in ("large_airport", "medium_airport") and a["scheduled"]) or a["iata"] in needed
        if keep:
            rows.append({"iata": a["iata"], "name": a["name"], "city": a["city"], "country": a["country"],
                         "lat": a["lat"], "lon": a["lon"], "size": "L" if a["type"] == "large_airport" else "M"})
    rows.sort(key=lambda r: r["iata"])
    return rows


def build_destinations(all_airports, tz_places):
    countries = []
    for r in parse_pipe_file(CURATED / "countries.txt"):
        cc, continent, rank, en, pt, es = r
        countries.append({"code": cc, "continent": continent, "rank": int(rank), "names": {"en": en, "pt": pt, "es": es}})
    cities = []
    for r in parse_pipe_file(CURATED / "cities.txt"):
        cid, cc, gateway, coord, min_nights, en, pt, es = r
        ap = all_airports.get(coord)
        if ap is None:
            raise SystemExit(f"coordinate airport {coord} for {cid} not found in OurAirports")
        tz = TZ_OVERRIDES.get(cid) or nearest_timezone(ap["lat"], ap["lon"], tz_places) or nearest_timezone(ap["lat"], ap["lon"], tz_places, max_km=400.0)
        if tz is None:
            raise SystemExit(f"no time zone for {cid}")
        cities.append({
            "id": cid, "country": cc, "gateway": gateway, "airport": coord, "lat": ap["lat"], "lon": ap["lon"],
            "tz": tz, "minNights": int(min_nights) if min_nights else None,
            "names": {"en": en, "pt": pt, "es": es},
        })
    cities.sort(key=lambda c: c["id"])
    countries.sort(key=lambda c: c["code"])
    return countries, cities


def build_climate(cities):
    out = {}
    for i, c in enumerate(cities, 1):
        url = ("https://power.larc.nasa.gov/api/temporal/climatology/point?parameters=T2M_MAX,T2M_MIN,PRECTOTCORR,RH2M"
               f"&community=RE&longitude={c['lon']}&latitude={c['lat']}&format=JSON")
        cache = RAW / "power" / f"{c['id']}.json"
        fresh = not cache.exists()
        d = fetch_json_cached(url, cache)
        p = d["properties"]["parameter"]
        out[c["id"]] = [
            [round(p["T2M_MAX"][m], 1), round(p["T2M_MIN"][m], 1), round(p["PRECTOTCORR"][m], 2), round(p["RH2M"][m], 1)]
            for m in MONTHS
        ]
        if fresh:
            time.sleep(1.0)
        if i % 20 == 0:
            print(f"climate {i}/{len(cities)}", file=sys.stderr)
    return out


def build_cyclones(cities):
    fetch(SOURCES["ibtracs.csv"], RAW / "ibtracs.csv")
    targets = [c for c in cities if abs(c["lat"]) <= 50]
    hits: dict[str, list[set]] = {c["id"]: [set() for _ in range(12)] for c in targets}
    lat_pad = CYCLONE_RADIUS_KM / 111.0 + 0.5
    with (RAW / "ibtracs.csv").open(encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        next(reader)  # units row
        ix = {n: i for i, n in enumerate(header)}
        iT, iLat, iLon, iW, iU, iS, iN = ix["ISO_TIME"], ix["LAT"], ix["LON"], ix["WMO_WIND"], ix["USA_WIND"], ix["SID"], ix["NATURE"]
        for row in reader:
            if row[iN] == "ET":  # extratropical: no longer a tropical cyclone
                continue
            try:
                wind = float(row[iW]) if row[iW].strip() else (float(row[iU]) if row[iU].strip() else 0.0)
            except ValueError:
                continue
            if wind < STORM_MIN_KT:
                continue
            lat, lon = float(row[iLat]), float(row[iLon])
            month = int(row[iT][5:7]) - 1
            for c in targets:
                if abs(c["lat"] - lat) > lat_pad:
                    continue
                if haversine_km(c["lat"], c["lon"], lat, lon) <= CYCLONE_RADIUS_KM:
                    hits[c["id"]][month].add(row[iS])
    years = LAST_YEAR - FIRST_YEAR + 1
    out = {}
    for c in cities:
        if c["id"] not in hits:
            out[c["id"]] = {"risk": ["LOW"] * 12, "rate": [0.0] * 12}
            continue
        rates = [round(len(s) / years, 3) for s in hits[c["id"]]]
        out[c["id"]] = {"risk": [classify_cyclone(r) for r in rates], "rate": rates}
    return out


def build_seismic(cities):
    out = {}
    for i, c in enumerate(cities, 1):
        url = ("https://earthquake.usgs.gov/fdsnws/event/1/count?format=geojson&minmagnitude=6&starttime=1976-01-01"
               f"&latitude={c['lat']}&longitude={c['lon']}&maxradiuskm=300")
        d = fetch_json_cached(url, RAW / "usgs" / f"{c['id']}.json")
        out[c["id"]] = {"m6Count": int(d["count"])}
        if i % 25 == 0:
            print(f"seismic {i}/{len(cities)}", file=sys.stderr)
    return out


MANIFEST_SOURCES = [
    {"name": "OurAirports", "url": "https://ourairports.com/data/", "license": "Public domain"},
    {"name": "GeoNames cities15000 (time zones)", "url": "https://www.geonames.org/", "license": "CC BY 4.0 (attribution required)"},
    {"name": "NASA POWER climatology", "url": "https://power.larc.nasa.gov/", "license": "Open data; attribute NASA POWER"},
    {"name": "NOAA IBTrACS v04r01 since1980", "url": "https://www.ncei.noaa.gov/products/international-best-track-archive", "license": "Open data; cite NOAA NCEI"},
    {"name": "USGS earthquake catalog (count)", "url": "https://earthquake.usgs.gov/fdsnws/event/1/", "license": "US government work (public domain)"},
    {"name": "Curated countries, cities, experiences", "url": "tools/catalog/curated", "license": "Project (MIT)"},
]


def main() -> None:
    all_airports = load_airports()
    tz_places = load_timezone_places()
    countries, cities = build_destinations(all_airports, tz_places)
    needed = {c["airport"] for c in cities}
    airports = build_airports(all_airports, needed)
    climate = build_climate(cities)
    cyclones = build_cyclones(cities)
    seismic = build_seismic(cities)
    experiences = parse_experiences(CURATED / "experiences.txt")

    stable_dump(airports, OUT_CATALOG / "airports.json")
    stable_dump(countries, OUT_CATALOG / "countries.json")
    stable_dump(cities, OUT_CATALOG / "destinations.json")
    stable_dump(climate, OUT_SERVER / "climate.json")
    stable_dump(cyclones, OUT_SERVER / "cyclones.json")
    stable_dump(seismic, OUT_SERVER / "seismic.json")
    stable_dump(experiences, OUT_SERVER / "experiences.json")
    inputs = {p.name: sha256(p) for p in (RAW / "airports.csv", RAW / "geonames.zip", RAW / "ibtracs.csv") if p.exists()}
    stable_dump({"generated": date.today().isoformat(), "sources": MANIFEST_SOURCES, "inputSha256": inputs,
                 "cycloneYears": [FIRST_YEAR, LAST_YEAR]}, OUT_SERVER / "manifest.json")
    print(f"ok: {len(airports)} airports, {len(countries)} countries, {len(cities)} cities")


if __name__ == "__main__":
    main()
