package bfp.client

import bfp.contracts.*
import kotlinx.serialization.Serializable

enum class SearchMode { EXPLORE, PLAN }
enum class CachedKind { EXPLORE, PLAN }

/** Stored last result (JSON). Shown only when offline and for the same query, labelled with its age. */
@Serializable
data class CachedResult(val kind: CachedKind, val payload: String, val savedAtMs: Long)

interface LastResultStore {
    suspend fun load(): String?
    suspend fun save(json: String)
}

sealed interface ResultState {
    data class Explore(val response: ExploreResponse, val fromCache: Boolean = false, val savedAtMs: Long? = null) : ResultState
    data class Plan(val response: RecommendationResponse, val fromCache: Boolean = false, val savedAtMs: Long? = null) : ResultState
}

data class OptionsState(val cityId: String, val yearMonth: String, val response: OptionsResponse)

data class SearchState(
    val origin: String = "",
    val destination: ScopeDto? = null,
    val dates: DatesDto = DatesDto.Anytime,
    val stay: StayDto = StayDto.Auto,
    val currency: String = "USD",
    val loading: Boolean = false,
    val error: ApiFailure? = null,
    val result: ResultState? = null,
    val options: OptionsState? = null,
) {
    val canSearch: Boolean get() = Regex("[A-Z]{3}").matches(origin)
    val mode: SearchMode get() = if (destination == null) SearchMode.EXPLORE else SearchMode.PLAN
    fun toExploreRequest() = ExploreRequest(origin, dates, currency)
    fun toRecommendationRequest() = RecommendationRequest(origin, destination ?: ScopeDto(ScopeKind.POPULAR), dates, stay, currency)
}

sealed interface SearchAction {
    data class SetOrigin(val value: String) : SearchAction
    data class SetDestination(val value: ScopeDto?) : SearchAction
    data class SetDates(val value: DatesDto) : SearchAction
    data class SetStay(val value: StayDto) : SearchAction
    data class SetCurrency(val value: String) : SearchAction
    data object Submit : SearchAction
    data object DismissError : SearchAction
    data class LoadOptions(val cityId: String, val yearMonth: String, val nights: Int?) : SearchAction
    data object CloseOptions : SearchAction
}

/** Pure reducer for the synchronous (input) part of the state machine. */
fun reduce(s: SearchState, a: SearchAction): SearchState = when (a) {
    is SearchAction.SetOrigin -> s.copy(origin = a.value.trim().uppercase())
    is SearchAction.SetDestination -> s.copy(destination = a.value)
    is SearchAction.SetDates -> s.copy(dates = a.value)
    is SearchAction.SetStay -> s.copy(stay = when (val v = a.value) {
        is StayDto.Nights -> StayDto.Nights(v.value.coerceIn(2, 21))
        StayDto.Auto -> StayDto.Auto
    })
    is SearchAction.SetCurrency -> s.copy(currency = a.value.trim().uppercase())
    SearchAction.DismissError -> s.copy(error = null)
    SearchAction.CloseOptions -> s.copy(options = null)
    SearchAction.Submit, is SearchAction.LoadOptions -> s
}
