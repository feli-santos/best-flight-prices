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
(empty until first full run)
