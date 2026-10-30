# Parity

Rule: every feature works the same on Android and iOS.

## Mechanisms
1. One UI, one client, one contract, server-side logic (~95% shared).
2. `make parity-check`: only allowlisted files under `androidMain`/`iosMain`/`desktopMain` of ui and client. Each actual documents identical behavior.
3. Shared Maestro flows (e2e/flows) run on both platforms.
4. Strings check: every key exists in en, pt, es. ReasonCode mapping is an exhaustive `when`.
5. Definition of done requires the matrix row below.

## Matrix
| Feature | Android | iOS | Maestro flow |
|---|---|---|---|
| Explore world | planned | planned | explore_world |
| Plan flexible | planned | planned | plan_japan_flexible |
| Country/continent scope | planned | planned | plan_country_scope |
| Exact dates -> options | planned | planned | exact_dates_options |
| Detail sheet + booking link | planned | planned | detail_link |
| Offline cached result | planned | planned | offline_cached |
| Error states | planned | planned | faults_* |
| pt / es locales | planned | planned | locale_* |
