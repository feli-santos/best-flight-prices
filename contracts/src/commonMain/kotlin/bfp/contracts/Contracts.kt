package bfp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire contract shared by server and clients. Additive-only within /v1. */
const val API_VERSION = "v1"

object ApiPaths {
    const val HEALTH = "/v1/health"
    const val EXPLORE = "/v1/explore"
    const val RECOMMENDATIONS = "/v1/recommendations"
    const val OPTIONS = "/v1/options"
    const val APP_VERSION_HEADER = "X-App-Version"
}

/** Shared JSON configuration: tolerant of new server fields, no defaults omitted surprises. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable enum class BandDto { GREAT, GOOD, MIXED, AVOID }
@Serializable enum class ConfidenceDto { HIGH, MEDIUM, LOW }
@Serializable enum class FareSource { TRAVELPAYOUTS, SAMPLE }
@Serializable enum class FareStatus { OK, MISSING, PENDING }
@Serializable enum class ScopeKind { PLACE, COUNTRY, CONTINENT, POPULAR }

@Serializable
enum class ReasonCodeDto {
    FARE_CHEAPEST, FARE_LOW, FARE_HIGH, FARE_MISSING,
    CLIMATE_PLEASANT, CLIMATE_HOT, CLIMATE_COLD, CLIMATE_WET, CLIMATE_HUMID,
    CYCLONE_HIGH, CYCLONE_MODERATE, EXPERIENCE,
    STAY_LONG_HAUL, STAY_TIME_ZONE, STAY_DESTINATION_MIN,
}

@Serializable data class ReasonDto(val code: ReasonCodeDto, val arg: String? = null)

/** Decimal string amount (never a float) plus ISO currency code. */
@Serializable
data class MoneyDto(val amount: String, val currency: String)

@Serializable
data class FareDto(
    val price: MoneyDto,
    /**
     * ISO-8601 instant when OUR server retrieved this fare. The provider's data is itself cached from
     * traveler searches of the previous ~48h, so the true age is this plus up to 48h.
     */
    val retrievedAt: String,
    val source: FareSource,
    /** Provider-supplied booking path/URL only; never fabricated. */
    val deepLink: String? = null,
    val transfers: Int? = null,
    val returnTransfers: Int? = null,
    val durationMinutes: Int? = null,
    val airline: String? = null,
    val departDate: String? = null,
    val returnDate: String? = null,
)

@Serializable
sealed interface DatesDto {
    @Serializable @SerialName("anytime") data object Anytime : DatesDto
    @Serializable @SerialName("range") data class Range(val from: String, val to: String) : DatesDto
    @Serializable @SerialName("exact") data class Exact(val depart: String, val ret: String? = null) : DatesDto
}

@Serializable
sealed interface StayDto {
    @Serializable @SerialName("auto") data object Auto : StayDto
    @Serializable @SerialName("nights") data class Nights(val value: Int) : StayDto
}

@Serializable data class ScopeDto(val kind: ScopeKind, val id: String? = null)

@Serializable
data class ExploreRequest(
    val origin: String,
    val dates: DatesDto = DatesDto.Anytime,
    val currency: String = "USD",
)

@Serializable
data class CityResult(val cityId: String, val fare: FareDto? = null, val fareStatus: FareStatus)

@Serializable
data class ExploreCountryResult(
    val countryCode: String,
    val domestic: Boolean = false,
    val cities: List<CityResult>,
    val bestMonths: List<Int> = emptyList(),
)

@Serializable
data class ExploreResponse(
    val origin: String,
    val countries: List<ExploreCountryResult>,
    val currency: String,
    val sample: Boolean,
    val generatedAt: String,
)

@Serializable
data class RecommendationRequest(
    val origin: String,
    val scope: ScopeDto,
    val dates: DatesDto = DatesDto.Anytime,
    val stay: StayDto = StayDto.Auto,
    val currency: String = "USD",
)

@Serializable
data class StayAdviceDto(val nights: Int, val candidates: List<Int>, val reasons: List<ReasonDto>)

@Serializable
data class MonthResult(
    val yearMonth: String,
    val band: BandDto,
    val confidence: ConfidenceDto,
    val fare: FareDto? = null,
    val fareStatus: FareStatus,
    val reasons: List<ReasonDto>,
)

@Serializable
data class PlaceRecommendation(
    val cityId: String,
    val stay: StayAdviceDto,
    val months: List<MonthResult>,
    val cheapest: FareDto? = null,
    /** Informational context only; never affects ranking. */
    val seismicNote: Boolean = false,
)

@Serializable
data class RecommendationResponse(
    val origin: String,
    val places: List<PlaceRecommendation>,
    val currency: String,
    val sample: Boolean,
    val generatedAt: String,
)

@Serializable
data class OptionsRequest(
    val origin: String,
    val cityId: String,
    val yearMonth: String,
    val nights: Int? = null,
    val currency: String = "USD",
)

@Serializable
data class OptionDto(val id: String, val nights: Int, val fare: FareDto)

@Serializable
data class OptionsResponse(
    val origin: String,
    val cityId: String,
    val options: List<OptionDto>,
    val currency: String,
    val sample: Boolean,
    val generatedAt: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    val apiVersion: String,
    val catalogVersion: String,
    val provider: String,
    val sample: Boolean,
)

/** RFC 7807 problem details, served as application/problem+json. */
@Serializable
data class Problem(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val retryAfterSeconds: Int? = null,
)

object ProblemTypes {
    const val INVALID_REQUEST = "invalid-request"
    const val UNKNOWN_ORIGIN = "unknown-origin"
    const val UNKNOWN_DESTINATION = "unknown-destination"
    const val RATE_LIMITED = "rate-limited"
    const val PROVIDER_UNAVAILABLE = "provider-unavailable"
    const val PROVIDER_UNAUTHORIZED = "provider-unauthorized"
    const val BUDGET_EXHAUSTED = "budget-exhausted"
    const val UPDATE_REQUIRED = "update-required"
    const val CURRENCY_UNSUPPORTED = "currency-unsupported"
}
