# Local lab

Everything is tested on this Mac before any phone. Lab gate: all rows green, recorded here with date and commit.

## Backends
make dev (sample, in-process) · make lab (fake-provider with fault scenarios) · make lab-real (real Travelpayouts, structure only) · make lab-deploy (podman image, read-only, non-root, 512 MB).

## Devices
Desktop harness (phone-size window, dev panel: size, language, dark, font scale, scenario) · Android emulators API 34 and API 29 (minSdk 29), release-like R8 build · iOS simulators small and large iPhone, Release-optimized build.
RAM 18 GiB: never run emulator and simulator together; make e2e runs them sequentially; Gradle -Xmx4g.

## Fake provider scenarios
normal, slow, timeout, rate_limited, unauthorized, server_error, malformed, success_false, empty, partial, no_brl, offline. Set via POST /__scenario. No real provider data is committed.

## Matrix
Domain, contract, adapter, ViewModels, screens, e2e Android (API 34, 29), e2e iOS (small, large), faults, network shaping (-netspeed edge), offline (Android airplane mode; iOS via scenario), locales, accessibility (font 2.0, dark), real data, performance indicators, secret scan of built APK/.app, deploy image.

## Not emulatable
Real hardware performance/thermal, real cellular networks, iOS free signing and 7-day expiry, real swipe-back/keyboard feel, attestation, store review.

## Gate record
- 2026-10-30 Phase 3: server routes (testApplication), provider adapter (MockEngine), fake-provider fault scenarios over real HTTP, podman image (read-only, UID 10001, 512 MB) serving /v1/health and /v1/explore. Note: Podman ignores the Dockerfile HEALTHCHECK (OCI format); use /v1/health directly.

## Gate record: Phase 4a (client, shared UI, desktop harness)
- `:client` (ApiClient, SearchViewModel, offline cache): 19 tests. `:server` includes `ClientContractTest` (real client vs real routes).
- `:ui` (Compose Multiplatform, desktop target): 30 tests, including every screen state in en/pt/es, 1x and 2x text, light and dark; strings parity for all reason codes and all 30 experiences.
- Run the harness: `make dev` (server, sample mode) then `./gradlew :desktopApp:run`.
- Screenshots: `./gradlew :ui:desktopTest --tests bfp.ui.GalleryTest -Dbfp.gallery=$PWD/.local/artifacts/gallery`.
- Not yet done: Android and iOS app shells (need Android SDK and Xcode), Maestro flows.

## Gate record: 2026-11-01 desktop harness end-to-end
- `make app` -> search `CNF` -> explore renders sample fares; server logs `200 OK: POST - /v1/explore`. `/v1/recommendations` and `/v1/options` checked by hand; invalid dates rejected with 400.
- Regression found: ViewModel scopes dispatch on `Dispatchers.Main`, which no desktop module provided, so the first search click crashed the window. `:desktopApp` now depends on `kotlinx-coroutines-swing` and `MainDispatcherTest` fails with the original message if it goes missing.
- `make test` now runs `./gradlew check`: 169 tests (`:core` 44, `:contracts` 8, `:client` 19, `:server` 60, `:ui` 30, `:desktopApp` 1).
- `make lab-real` runs the server against the real provider; it has not been run yet (no token).
