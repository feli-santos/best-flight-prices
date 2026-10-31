package bfp.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bfp.contracts.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single ViewModel for the three screens. A new search cancels the previous one, so a stale response can
 * never overwrite a newer one. Offline, a saved result for the SAME query is shown, labelled as cached.
 */
class SearchViewModel(
    private val repo: TripRepository,
    private val store: LastResultStore,
    scope: CoroutineScope? = null,
    private val nowMs: () -> Long = { 0L },
) : ViewModel() {
    private val scope: CoroutineScope = scope ?: viewModelScope
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var optionsJob: Job? = null

    fun dispatch(a: SearchAction) {
        when (a) {
            SearchAction.Submit -> submit()
            is SearchAction.LoadOptions -> loadOptions(a)
            SearchAction.CloseOptions -> { optionsJob?.cancel(); _state.update { reduce(it, a) } }
            else -> _state.update { reduce(it, a) }
        }
    }

    private fun submit() {
        val s = _state.value
        if (!s.canSearch) return
        searchJob?.cancel()
        _state.update { it.copy(loading = true, error = null, options = null) }
        searchJob = scope.launch {
            try {
                when (s.mode) {
                    SearchMode.EXPLORE -> handle(s, repo.explore(s.toExploreRequest())) { ResultState.Explore(it) to CachedResult(CachedKind.EXPLORE, ApiJson.encodeToString(ExploreResponse.serializer(), it), nowMs()) }
                    SearchMode.PLAN -> handle(s, repo.recommendations(s.toRecommendationRequest())) { ResultState.Plan(it) to CachedResult(CachedKind.PLAN, ApiJson.encodeToString(RecommendationResponse.serializer(), it), nowMs()) }
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private suspend fun <T> handle(sent: SearchState, r: ApiResult<T>, wrap: (T) -> Pair<ResultState, CachedResult>) {
        when (r) {
            is ApiResult.Ok -> {
                val (result, cache) = wrap(r.value)
                _state.update { it.copy(loading = false, error = null, result = result) }
                runCatching { store.save(ApiJson.encodeToString(CachedResult.serializer(), cache)) }
            }
            is ApiResult.Failure -> {
                val cached = if (r.failure == ApiFailure.Offline) cachedFor(sent) else null
                _state.update { it.copy(loading = false, error = r.failure, result = cached ?: it.result) }
            }
        }
    }

    private suspend fun cachedFor(sent: SearchState): ResultState? {
        val raw = runCatching { store.load() }.getOrNull() ?: return null
        val c = runCatching { ApiJson.decodeFromString(CachedResult.serializer(), raw) }.getOrNull() ?: return null
        return runCatching {
            when (c.kind) {
                CachedKind.EXPLORE -> ApiJson.decodeFromString(ExploreResponse.serializer(), c.payload)
                    .takeIf { sent.mode == SearchMode.EXPLORE && it.origin == sent.origin && it.currency == sent.currency }
                    ?.let { ResultState.Explore(it, fromCache = true, savedAtMs = c.savedAtMs) }
                CachedKind.PLAN -> ApiJson.decodeFromString(RecommendationResponse.serializer(), c.payload)
                    .takeIf { sent.mode == SearchMode.PLAN && it.origin == sent.origin && it.currency == sent.currency }
                    ?.let { ResultState.Plan(it, fromCache = true, savedAtMs = c.savedAtMs) }
            }
        }.getOrNull()
    }

    private fun loadOptions(a: SearchAction.LoadOptions) {
        val s = _state.value
        if (!s.canSearch) return
        optionsJob?.cancel()
        optionsJob = scope.launch {
            _state.update { it.copy(error = null) }
            when (val r = repo.options(OptionsRequest(s.origin, a.cityId, a.yearMonth, a.nights, s.currency))) {
                is ApiResult.Ok -> _state.update { it.copy(options = OptionsState(a.cityId, a.yearMonth, r.value)) }
                is ApiResult.Failure -> _state.update { it.copy(error = r.failure) }
            }
        }
    }
}
