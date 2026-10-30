# 0001. Kotlin Multiplatform with Compose Multiplatform

Status: Accepted (2026)

## Decision and context
Android and iOS must behave identically and be cheap to maintain. Use KMP with a shared Compose UI (iOS, Android and Desktop are Stable) in one Gradle monorepo with a thin iOS host. Rejected: native UIs x2 (parity by discipline), Skip (vendor toolchain risk), Flutter (second language with backend).

## Consequences
See docs/ARCHITECTURE.md and docs/PARITY.md.
