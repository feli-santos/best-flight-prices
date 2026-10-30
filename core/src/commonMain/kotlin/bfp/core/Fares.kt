package bfp.core

import kotlinx.datetime.LocalDate

enum class FareSourceKind { TRAVELPAYOUTS, SAMPLE }

/** A provider fare. Prices are minor units (hundredths) of [currency]; never floating point. */
data class FareQuote(
    val origin: String,
    val destination: String,
    val destinationAirport: String?,
    val departDate: LocalDate,
    val returnDate: LocalDate?,
    val priceMinor: Long,
    val currency: String,
    val transfers: Int?,
    val returnTransfers: Int?,
    val durationMinutes: Int?,
    val airline: String?,
    val deepLink: String?,
    val retrievedAtEpochMs: Long,
    val source: FareSourceKind,
)

class ProviderException(
    val kind: Kind,
    message: String,
    val retryAfterSeconds: Int? = null,
) : Exception(message) {
    enum class Kind { UNAUTHORIZED, RATE_LIMITED, UNAVAILABLE, BAD_RESPONSE, CURRENCY_UNSUPPORTED, BUDGET_EXHAUSTED }
}

/** Port for fare data. Implementations: Travelpayouts (cached provider data) and a labeled sample model. */
interface FareProvider {
    val name: String
    val isSample: Boolean

    /** Cheapest round-trip fare per departure month for a route, for trips of [tripDays] days. */
    suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange): Map<YearMonth, FareQuote>

    /** Round-trip fares departing in [depart] and returning in [returnMonth]. */
    suspend fun roundTrips(origin: String, destination: String, depart: YearMonth, returnMonth: YearMonth, currency: String): List<FareQuote>

    /** Cheapest recent round-trip fare to each destination from an origin (optionally for one departure month). */
    suspend fun cheapestByDestination(origin: String, depart: YearMonth?, currency: String): List<FareQuote>
}
