# Changelog
All notable changes follow Keep a Changelog; versions follow SemVer.

## [Unreleased]
- Repository foundation, documentation and architecture decisions.

### Fixed
- Desktop harness crashed on the first search (`Module with the Main dispatcher is missing`): the app now ships `kotlinx-coroutines-swing`, guarded by `MainDispatcherTest`.

### Changed
- `make test` runs `./gradlew check`, so `:server` and `:ui` suites are no longer skipped.
- Added `make lab-real` for structural checks against the real fare provider.
- `TRAVELPAYOUTS_MARKER` documented in `.env.example` and docs/DEVELOPMENT.md.
