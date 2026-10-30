# Data sources and licenses

| Data | Source | License | Use |
|---|---|---|---|
| Airports | OurAirports | Public domain | catalog |
| Time zones | GeoNames cities15000 (nearest place, with a small override table) | CC BY 4.0; attribute GeoNames | catalog |
| Climate normals | NASA POWER climatology | Open; attribute NASA POWER | server |
| Cyclone tracks | NOAA IBTrACS v04r01 since-1980 (tropical storms >=34 kt within 300 km, extratropical excluded, 1980-2025) | Open; cite NOAA NCEI | server |
| Earthquakes M6+ | USGS | US government work | info line only |
| Top-50 countries | Curated list informed by UN Tourism arrivals rankings (not an exact ranking; reviewed yearly) | Facts; cite | catalog |
| Experiences | Hand-curated, source URL per entry | own work | server |
| Fares | Travelpayouts Data API (cached) | provider terms | server |

**Excluded:** GEM Global Seismic Hazard Map (CC BY-NC-SA, incompatible with MIT).
Fares: cached from searches in the last 48h, retained up to 7 days; we cache 24h; booking links only as supplied. Raw provider responses are never committed. `manifest.json` records source, license, date, sha256.

## Regenerating
`python3 tools/catalog/build_catalog.py` (downloads to `.local/raw`, ~5 min because NASA POWER is throttled to 1 req/s), then `python3 tools/catalog/validate_catalog.py`. Outputs: `data/catalog` (bundled in apps, <1.5 MB) and `data/server` (server only, <5 MB). Cyclone rate = storms per year; LOW <0.05, MODERATE <0.20, HIGH >=0.20.
