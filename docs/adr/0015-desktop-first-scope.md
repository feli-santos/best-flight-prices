# 0015 Desktop-first release

Status: accepted (2026-10-31)

## Context
Android and iOS shells need the Android SDK, Xcode 27 and a device lab, which are not set up yet. The owner wants to try the product first.

## Decision
Ship v0.1.0 as a desktop app (Compose Multiplatform JVM) talking to the same backend. Mobile ports go to the backlog. Code stays multiplatform-ready: UI in `commonMain`, logic in `:core`, `:contracts`, `:client`; platform needs go through the `Platform` interface.

## Consequences
- ADR 0001 and 0007 (parity) still describe the target; they apply once mobile shells exist.
- `:client` and `:ui` currently have only JVM/desktop targets; adding Android/iOS targets is part of the port.
- Desktop-only dev panel is hidden unless `BFP_DEVPANEL=1`.
