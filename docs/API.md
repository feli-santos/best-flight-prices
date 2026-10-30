# API (v1)

JSON over HTTPS. Errors: `application/problem+json`. Prices: decimal string + currency code. Header `X-App-Version`; below MIN_APP_VERSION the server returns 426.

| Route | Purpose |
|---|---|
| GET /v1/health | liveness, catalog and data versions |
| POST /v1/explore | origin only -> top-50 countries (<=3 cities each) |
| POST /v1/recommendations | origin + scope + dates + stay -> months (single place) or ranked destinations |
| POST /v1/options | origin + destination + month + nights -> ranked round trips |

Responses carry `stay{nights,reasonCodes}`, `months[]{yearMonth,band,confidence,fare?,reasonCodes<=2}`, and `fare{price{amount,currency},retrievedAt,source,deepLink?,transfers?,durationMinutes?,...}`. Each request has an 8s deadline; unfinished destinations return `fareStatus: "pending"`. Contract changes inside /v1 are additive only. Detailed schemas live in the contracts module and golden JSON tests.

`retrievedAt` is when our server fetched the fare; the provider's data is itself cached from traveler searches of the previous ~48h. Errors map to problem types: invalid-request, unknown-origin, unknown-destination, rate-limited (429), provider-unavailable (503), provider-unauthorized (502), budget-exhausted (503), update-required (426), currency-unsupported (400).
