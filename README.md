# BestFlightPrice

Find the cheapest and best time to fly — on iPhone and Android, with identical behavior.

- **Explore the world:** pick only an origin and see the cheapest recent fares to the 50 most-visited countries (and their top cities).
- **Plan a trip:** pick a destination and get the best months (price, climate, cyclones, signature experiences like Uyuni's mirror season), a sensible stay length, and ranked round trips.
- Languages: English, Português (Brasil), Español.

> Status: under construction (see docs/ROADMAP.md). Fares are cached provider data, not live prices.

## Stack
Kotlin Multiplatform + Compose Multiplatform (Android, iOS, desktop dev harness) · Ktor server · Travelpayouts cached fares. See docs/ARCHITECTURE.md.

## Quick start
```
make lab-setup     # one-time toolchain setup (see docs/DEVELOPMENT.md)
make dev           # server (sample data) + desktop harness
make test          # unit + screen tests
make e2e           # Maestro on Android emulator and iOS simulator
```

## Docs
docs/PRODUCT.md · ARCHITECTURE.md · PARITY.md · API.md · SEARCH_AND_SCORING.md · DATA_SOURCES.md · TESTING.md · LOCAL_LAB.md · LOCALIZATION.md · DEVELOPMENT.md · BACKEND_DEPLOY.md · DEVICE_INSTALL.md · PRIVACY.md · SECURITY.md · ROADMAP.md · GLOSSARY.md · adr/

## License
MIT — see LICENSE. Third-party data attributions: docs/DATA_SOURCES.md.
