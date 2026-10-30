# AGENTS.md — BestFlightPrice

Read first: README.md, docs/PRODUCT.md, docs/ARCHITECTURE.md, docs/PARITY.md, docs/SEARCH_AND_SCORING.md, docs/LOCAL_LAB.md, docs/ROADMAP.md, docs/adr/.

## Engineering
- Kotlin Multiplatform monorepo: Android + iOS share one UI (Compose Multiplatform), one client, one contract; the Ktor server owns the domain logic.
- Small vertical slices, TDD. `core` is pure (no I/O, no clock reads: inject `Clock`).
- **Parity rule:** every feature ships on Android and iOS together. Platform code lives only in the allowlisted `platform/` actuals (`make parity-check`).
- Definition of done: contract -> server -> client -> UI -> strings in en/pt/es -> Maestro flow green on both platforms -> docs/PARITY.md row.
- Server returns reason codes, never prose. All user text lives in Compose string resources (en, pt, es).
- Simple UI wins: 3 screens + sheet, no maps/photos/charts.

## Data and providers
- Fares come only through `FareProvider`. Never invent prices, dates or booking links. Missing data is shown as missing.
- Sample data is always labeled. Real mode never falls back to sample.
- Seismic data never affects ranking. No scraping. Only MIT-compatible data licenses (docs/DATA_SOURCES.md).
- Never commit or print secrets (.env is gitignored). The fare token lives only on the server.

## Local lab (RAM: 18 GiB)
- Test everything on this Mac first (docs/LOCAL_LAB.md). Never run the Android emulator and iOS simulator at the same time; use `make e2e`.

## Verification and git
- Run the relevant `make` target and report real results; say so if something was not verified.
- Smallest change, conventional commits, no force-push, no new dependency/cost/security relaxation without asking.
- New decisions need a new ADR (supersede, never rewrite).
