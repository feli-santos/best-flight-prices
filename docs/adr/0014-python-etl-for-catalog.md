# 0014. Python standard-library ETL for the catalog

Status: Accepted (2026). Amends ADR 0001/0013 (planned a Kotlin catalog-builder).

## Decision and context
The catalog builder downloads CSV/ZIP/JSON sources and writes JSON. Python's standard library does this with far less code than a Kotlin tool (CSV, zipfile, urllib). It is a build-time tool only; nothing ships in the apps. It is unit-tested with `unittest`, and the Kotlin `catalog` module validates the generated JSON in its own tests, so schema drift is caught on the Kotlin side.

## Consequences
Python 3 is a developer prerequisite (already present on macOS). Generated JSON is committed; raw downloads stay in `.local/` (gitignored). GeoNames (CC BY 4.0) is added for time zones and needs attribution.
