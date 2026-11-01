# BestFlightPrice

Find the cheapest and best time to fly.

**Current release target: desktop app (macOS/Windows/Linux, JVM).** Android and iOS ports are on the backlog; the UI is already shared Compose Multiplatform code, so they need app shells only (see docs/ROADMAP.md).

- **Explore the world:** pick only an origin and see the cheapest recent fares to the 50 most-visited countries (and their top cities).
- **Plan a trip:** pick a destination and get the best months (price, climate, cyclones, signature experiences like Uyuni's mirror season), a sensible stay length, and ranked round trips.
- Languages: English, Português (Brasil), Español.

> Status: under construction (see docs/ROADMAP.md). Fares are cached provider data, not live prices.

## Stack
Kotlin Multiplatform + Compose Multiplatform (Android, iOS, desktop dev harness) · Ktor server · Travelpayouts cached fares. See docs/ARCHITECTURE.md.

## Run it
Requires JDK 21 (`brew install openjdk@21`).
```
make app           # starts the backend and opens the app; sample data unless .env has a token
make app-dev       # same, plus a dev panel (language, dark mode, text size)
make test          # all unit, contract and screen tests
```
Without a `TRAVELPAYOUTS_TOKEN` in `.env` the app shows **sample data** with a visible banner (made-up prices). With a token it shows real, cached fares. Copy `.env.example` to `.env` to configure.

## Docs
docs/PRODUCT.md · ARCHITECTURE.md · PARITY.md · API.md · SEARCH_AND_SCORING.md · DATA_SOURCES.md · TESTING.md · LOCAL_LAB.md · LOCALIZATION.md · DEVELOPMENT.md · BACKEND_DEPLOY.md · DEVICE_INSTALL.md · PRIVACY.md · SECURITY.md · ROADMAP.md · GLOSSARY.md · adr/

## License
MIT — see LICENSE. Third-party data attributions: docs/DATA_SOURCES.md.
