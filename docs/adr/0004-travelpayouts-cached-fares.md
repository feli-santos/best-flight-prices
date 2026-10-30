# 0004. Travelpayouts cached fares

Status: Accepted (2026)

## Decision and context
Amadeus Self-Service is being shut down. Travelpayouts Data API provides cached fares (48h, kept 7 days). Fares mean 'recent lowest price', not live or historical. Links only as provided; 24h cache; rate limit 600/min on v3 endpoints.

## Consequences
See docs/ARCHITECTURE.md and docs/PARITY.md.
