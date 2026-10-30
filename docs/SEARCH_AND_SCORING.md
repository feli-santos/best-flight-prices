# Search and scoring

## Stay advisor
Journey hours: provider duration if known, else km/800 + 0.5 + 2 x connections (0 if <3000 km, 1 if <9000, else 2).
Base nights: <=4h 3; <=8h 5; <=14h 7; <=22h 10; else 12. +1 if time-zone shift >=8h. Apply destination minimum. Clamp 2-21. Candidates: base-2, base, base+2, base+4.
Golden: CNF->GRU 3; CNF->LIS 7; CNF->Tokyo >=10; CNF->SYD >=12.

## Month scoring (0-1 per factor)
- Fare: clamp(1 - (p - pMin)/pMin). Missing fare = factor missing.
- Climate: 0.5 temp (1.0 at 18-28 C max, 0 at 8/36) + 0.4 rain (1.0 <2 mm/day, 0 at 8) + 0.1 humidity penalty.
- Experience: peak 1.0, shoulder 0.7, else 0.3; missing if none defined.
- Cyclone: low 1.0, moderate 0.6, high 0.1.
Weights: fare .30, climate .30, experience .25, cyclone .15; missing factors renormalized. Bands: >=.75 Great, >=.60 Good, >=.45 Mixed, else Avoid. High cyclone caps at Mixed; a climate factor below 0.30 also caps at Mixed. Seismic data is not an input (enforced by the scorer signature).
Confidence: high (all factors, fare <7 days), medium (fare missing), low (climate missing). Reasons: two most influential factors (a peak signature experience is always surfaced first); fareMissing always shown when absent.

## Itinerary ranking
Price 55%, total duration 25%, stops 20% (min-max within the set; missing duration = median). Tie-break price then departure date; max 20.

## Explore
Top-50 countries (docs/DATA_SOURCES.md), up to 3 cities each. Each country shows cheapest city fare and best months from climate/experiences (no provider cost). No-fare countries last. Origin city excluded; domestic flagged. Deterministic order.

## Fare meaning
Fare signal = lowest recently seen price for travel in that month, not a historical average. The UI says so.
