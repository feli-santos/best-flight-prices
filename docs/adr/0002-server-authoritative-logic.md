# 0002. Server-authoritative recommendations

Status: Accepted (2026)

## Decision and context
Scoring, stay and ranking run on the Ktor server; apps send intent and render results with reason codes. Parity is automatic and logic fixes need no store release. Trade-off: no fresh results offline; last results cached on device with age.

## Consequences
See docs/ARCHITECTURE.md and docs/PARITY.md.
