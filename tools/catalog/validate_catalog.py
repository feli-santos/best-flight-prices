#!/usr/bin/env python3
"""Validates generated catalog data: schema, references, size budgets, golden checks."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CAT, SRV = ROOT / "data" / "catalog", ROOT / "data" / "server"
APP_BUDGET, SERVER_BUDGET = 1_500_000, 5_000_000
TZ_COVERAGE_MIN = 0.90


def load(p: Path):
    return json.loads(p.read_text(encoding="utf-8"))


def validate(cat: Path = CAT, srv: Path = SRV) -> list[str]:
    errs: list[str] = []
    airports, countries, cities = load(cat / "airports.json"), load(cat / "countries.json"), load(cat / "destinations.json")
    climate, cyc, seis, exps = (load(srv / n) for n in ("climate.json", "cyclones.json", "seismic.json", "experiences.json"))

    iatas = {a["iata"] for a in airports}
    codes = {c["code"] for c in countries}
    ids = [c["id"] for c in cities]
    if len(ids) != len(set(ids)):
        errs.append("duplicate city ids")
    ranked = [c for c in countries if c["rank"] > 0]
    if len(ranked) != 50 or sorted(c["rank"] for c in ranked) != list(range(1, 51)):
        errs.append(f"expected top-50 ranks 1..50, got {len(ranked)}")
    per_country: dict[str, int] = {}
    for c in cities:
        per_country[c["country"]] = per_country.get(c["country"], 0) + 1
        if c["country"] not in codes:
            errs.append(f"{c['id']}: unknown country {c['country']}")
        if c["airport"] not in iatas:
            errs.append(f"{c['id']}: airport {c['airport']} not in airports.json")
        for name, table in (("climate", climate), ("cyclones", cyc), ("seismic", seis)):
            if c["id"] not in table:
                errs.append(f"{c['id']}: missing {name}")
        if c["id"] in climate and (len(climate[c["id"]]) != 12 or any(len(m) != 4 for m in climate[c["id"]])):
            errs.append(f"{c['id']}: climate must be 12x4")
        if c["id"] in cyc and (len(cyc[c["id"]]["risk"]) != 12):
            errs.append(f"{c['id']}: cyclone must have 12 months")
        if not all(c["names"].get(l) for l in ("en", "pt", "es")):
            errs.append(f"{c['id']}: missing translated name")
    for cc in codes:
        n = per_country.get(cc, 0)
        if not 1 <= n <= 3:
            errs.append(f"country {cc} has {n} cities (need 1..3)")
        if not all(next(x for x in countries if x["code"] == cc)["names"].get(l) for l in ("en", "pt", "es")):
            errs.append(f"country {cc}: missing translated name")
    for e in exps:
        for d in e["destinations"]:
            if d not in ids:
                errs.append(f"experience {e['id']}: unknown destination {d}")
        if not e["peak"]:
            errs.append(f"experience {e['id']}: no peak months")
    app_size = sum(p.stat().st_size for p in cat.glob("*.json"))
    srv_size = sum(p.stat().st_size for p in srv.glob("*.json"))
    if app_size > APP_BUDGET:
        errs.append(f"app catalog {app_size} bytes exceeds budget {APP_BUDGET}")
    if srv_size > SERVER_BUDGET:
        errs.append(f"server data {srv_size} bytes exceeds budget {SERVER_BUDGET}")
    with_tz = sum(1 for a in airports if a.get('tz'))
    if airports and with_tz / len(airports) < TZ_COVERAGE_MIN:
        errs.append(f'only {with_tz}/{len(airports)} airports have a time zone')
    # golden checks
    by_id = {c["id"]: c for c in cities}
    if "jp-tokyo" in by_id and by_id["jp-tokyo"]["tz"] != "Asia/Tokyo":
        errs.append("golden: Tokyo tz")
    if "bo-uyuni" in by_id:
        uy = next((e for e in exps if e["id"] == "uyuni-mirror"), None)
        if not uy or not {1, 2, 3} <= set(uy["peak"]):
            errs.append("golden: Uyuni mirror season Jan-Mar")
    if "pt-lisbon" in cyc and set(cyc["pt-lisbon"]["risk"]) != {"LOW"}:
        errs.append("golden: Lisbon cyclone risk must be LOW all year")
    if "us-miami" in cyc and cyc["us-miami"]["risk"][8] == "LOW":
        errs.append("golden: Miami September cyclone risk must not be LOW")
    if "do-punta-cana" in cyc and cyc["do-punta-cana"]["risk"][8] == "LOW":
        errs.append("golden: Punta Cana September cyclone risk must not be LOW")
    return errs


if __name__ == "__main__":
    e = validate()
    print("\n".join(e) if e else "catalog-validate: ok")
    sys.exit(1 if e else 0)
