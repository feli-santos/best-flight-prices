# Architecture

Kotlin Multiplatform monorepo. Server is authoritative for recommendations; apps render them.

```
androidApp / iosApp (thin hosts)  ->  :ui (Compose MP)  ->  :client (Ktor client, ViewModels)  ->  HTTPS
                                                                 |                                    |
                                                          :contracts (DTOs)  <------------------>  :server (Ktor, JVM)
                                                          :catalog (airports, destinations)           |-> :core (pure domain)
                                                                                                      |-> FareProvider: Travelpayouts | Sample
desktopApp = dev harness (same :ui)        :fake-provider = Travelpayouts-shaped test double
```

## Modules
- **core** — pure Kotlin (commonMain): models, StayAdvisor, MonthScorer, ItineraryRanker, DatePlanner, ExploreRanker, ports (FareProvider, Clock). No I/O.
- **contracts** — @Serializable DTOs, ReasonCode, API version, golden JSON tests. The only client/server coupling.
- **catalog** — bundled airports, destinations, top-50 countries and loader.
- **server** — routes, providers, cache, rate limit, budget, server-only data (climate, cyclones, experiences, seismic).
- **client** — API client, repositories, MVI ViewModels, last-result store.
- **ui** — screens, theme, navigation, string resources; `platform/` expect/actual limited to UrlOpener, ShareSheet, AppInfo, formatters.
- **fake-provider**, **desktopApp**, **androidApp**, **iosApp**, **tools/catalog-builder**.

## Rules
- Dependencies point inward: ui -> client -> contracts/catalog; server -> core/contracts/catalog. core depends on nothing but kotlinx-datetime.
- State: immutable State + sealed Action per screen, ViewModel exposes StateFlow; reducers are pure.
- DI: hand-written AppGraph(AppConfig). No framework.
- Server is stateless, 12-factor, containerized; host chosen later (ADR 0009).
- Public API controls: per-IP rate limit, validation, daily provider budget, 24h fare cache, min app version (HTTP 426).

See docs/adr/ for decisions and PARITY.md for how parity is enforced.
