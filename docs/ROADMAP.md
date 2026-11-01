# Roadmap

## Now: desktop release (v0.1.0)
Done: core domain, catalog data, server, fake provider, client, shared UI (en/pt/es), desktop app (`make app`).
Remaining before tagging v0.1.0: real-provider smoke test (needs `TRAVELPAYOUTS_TOKEN`), owner feedback pass.

## Backlog: mobile ports
The UI and logic are shared Compose Multiplatform code. Mobile needs only:
1. **Android shell** (`androidApp/`): Android SDK, `com.android.kotlin.multiplatform.library`, minSdk 29, Maestro flows.
2. **iOS shell** (`iosApp/`): Xcode 27, XcodeGen wrapper hosting `ComposeUIViewController`, iOS targets for `:client` and `:ui`, Darwin Ktor engine, `NSAllowsLocalNetworking`.
3. Platform actuals: DataStore-backed `LastResultStore`, `Platform` (open URL, locale), native date picker.
4. Parity enforcement and the lab gate (see docs/PARITY.md, docs/LOCAL_LAB.md).
5. Stores: Apple $99/yr; Google $25 + 12 testers x 14 days.

## Later
Hosted backend (ADR 0009), attestation, price alerts, native date picker on desktop.
