package bfp.client

import bfp.contracts.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val exploreOk = ExploreResponse("CNF", listOf(ExploreCountryResult("PT", false, listOf(CityResult("pt-lisbon", null, FareStatus.MISSING)))), "BRL", true, "t")
    private val recOk = RecommendationResponse("CNF", listOf(PlaceRecommendation("pt-lisbon", StayAdviceDto(7, listOf(7), emptyList()), emptyList())), "BRL", true, "t")

    private class FakeRepo : TripRepository {
        var explore: suspend (ExploreRequest) -> ApiResult<ExploreResponse> = { ApiResult.Failure(ApiFailure.Offline) }
        var rec: suspend (RecommendationRequest) -> ApiResult<RecommendationResponse> = { ApiResult.Failure(ApiFailure.Offline) }
        var opts: suspend (OptionsRequest) -> ApiResult<OptionsResponse> = { ApiResult.Failure(ApiFailure.Offline) }
        override suspend fun explore(r: ExploreRequest) = explore.invoke(r)
        override suspend fun recommendations(r: RecommendationRequest) = rec.invoke(r)
        override suspend fun options(r: OptionsRequest) = opts.invoke(r)
    }

    private class FakeStore : LastResultStore {
        var saved: String? = null
        override suspend fun load(): String? = saved
        override suspend fun save(json: String) { saved = json }
    }

    private fun vm(repo: FakeRepo, store: FakeStore = FakeStore(), scope: kotlinx.coroutines.CoroutineScope) = SearchViewModel(repo, store, scope)

    /** A scope on the same scheduler as runTest, so advanceUntilIdle() drives ViewModel work. */
    private fun kotlinx.coroutines.test.TestScope.vmScope() = kotlinx.coroutines.CoroutineScope(StandardTestDispatcher(testScheduler) + kotlinx.coroutines.SupervisorJob())

    @Test fun reducerRequiresOriginAndBuildsRequest() {
        val s = SearchState()
        assertEquals(false, s.canSearch)
        val ready = reduce(s, SearchAction.SetOrigin("CNF"))
        assertTrue(ready.canSearch)
        assertEquals(StayDto.Auto, ready.toRecommendationRequest().stay)
        val place = reduce(reduce(ready, SearchAction.SetDestination(ScopeDto(ScopeKind.PLACE, "jp-tokyo"))), SearchAction.SetStay(StayDto.Nights(9)))
        assertEquals(ScopeDto(ScopeKind.PLACE, "jp-tokyo"), place.toRecommendationRequest().scope)
        assertEquals(StayDto.Nights(9), place.toRecommendationRequest().stay)
    }

    @Test fun nightsAreClampedByReducer() {
        assertEquals(StayDto.Nights(21), reduce(SearchState(), SearchAction.SetStay(StayDto.Nights(99))).stay)
        assertEquals(StayDto.Nights(2), reduce(SearchState(), SearchAction.SetStay(StayDto.Nights(0))).stay)
    }

    @Test fun originIsUppercasedAndTrimmed() =
        assertEquals("CNF", reduce(SearchState(), SearchAction.SetOrigin(" cnf ")).origin)

    @Test fun noDestinationMeansExplore() {
        assertEquals(SearchMode.EXPLORE, reduce(SearchState(), SearchAction.SetOrigin("CNF")).mode)
        assertEquals(SearchMode.PLAN, reduce(reduce(SearchState(), SearchAction.SetOrigin("CNF")), SearchAction.SetDestination(ScopeDto(ScopeKind.COUNTRY, "JP"))).mode)
    }

    @Test fun exploreSuccessThenSavesLastResult() = runTest {
        val repo = FakeRepo().apply { explore = { ApiResult.Ok(exploreOk) } }
        val store = FakeStore()
        val vm = vm(repo, store, vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        val r = assertIs<ResultState.Explore>(vm.state.value.result)
        assertEquals(1, r.response.countries.size)
        assertTrue(store.saved != null)
        assertNull(vm.state.value.error)
    }

    @Test fun planSuccess() = runTest {
        val repo = FakeRepo().apply { rec = { ApiResult.Ok(recOk) } }
        val vm = vm(repo, scope = vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.SetDestination(ScopeDto(ScopeKind.PLACE, "pt-lisbon"))); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        assertIs<ResultState.Plan>(vm.state.value.result)
    }

    @Test fun failureKeepsPreviousResultAndShowsError() = runTest {
        var fail = false
        val repo = FakeRepo().apply { explore = { if (fail) ApiResult.Failure(ApiFailure.RateLimited(7)) else ApiResult.Ok(exploreOk) } }
        val vm = vm(repo, scope = vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        fail = true
        vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        assertEquals(ApiFailure.RateLimited(7), vm.state.value.error)
        assertIs<ResultState.Explore>(vm.state.value.result)
        assertEquals(false, vm.state.value.loading)
    }

    @Test fun offlineFallsBackToCachedLastResultLabelled() = runTest {
        val store = FakeStore().apply { saved = ApiJson.encodeToString(CachedResult(CachedKind.EXPLORE, ApiJson.encodeToString(exploreOk), savedAtMs = 1000)) }
        val repo = FakeRepo() // offline
        val vm = vm(repo, store, vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.SetCurrency("BRL")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        assertEquals(ApiFailure.Offline, vm.state.value.error)
        val r = assertIs<ResultState.Explore>(vm.state.value.result)
        assertTrue(r.fromCache)
        assertEquals(1000L, r.savedAtMs)
    }

    @Test fun cachedResultForADifferentOriginIsNotShown() = runTest {
        val store = FakeStore().apply { saved = ApiJson.encodeToString(CachedResult(CachedKind.EXPLORE, ApiJson.encodeToString(exploreOk), 1000)) }
        val vm = vm(FakeRepo(), store, vmScope())
        vm.dispatch(SearchAction.SetOrigin("GRU")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        assertNull(vm.state.value.result)
    }

    @Test fun newSearchCancelsStaleOne() = runTest {
        val gate = CompletableDeferred<ApiResult<ExploreResponse>>()
        var calls = 0
        val repo = FakeRepo().apply { explore = { calls++; if (calls == 1) gate.await() else ApiResult.Ok(exploreOk.copy(origin = "GRU")) } }
        val vm = vm(repo, scope = vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        vm.dispatch(SearchAction.SetOrigin("GRU")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        gate.complete(ApiResult.Ok(exploreOk)) // stale result arrives late
        advanceUntilIdle()
        assertEquals("GRU", assertIs<ResultState.Explore>(vm.state.value.result).response.origin)
    }

    @Test fun loadingFlagLifecycle() = runTest {
        val gate = CompletableDeferred<ApiResult<ExploreResponse>>()
        val repo = FakeRepo().apply { explore = { gate.await() } }
        val vm = vm(repo, scope = vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        assertTrue(vm.state.value.loading)
        gate.complete(ApiResult.Ok(exploreOk)); advanceUntilIdle()
        assertEquals(false, vm.state.value.loading)
    }

    @Test fun optionsLoadForSelectedMonth() = runTest {
        val opt = OptionsResponse("CNF", "pt-lisbon", emptyList(), "BRL", true, "t")
        val repo = FakeRepo().apply { opts = { ApiResult.Ok(opt) } }
        val vm = vm(repo, scope = vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.LoadOptions("pt-lisbon", "2027-04", 7)); advanceUntilIdle()
        assertEquals(opt, vm.state.value.options?.response)
    }

    @Test fun cachedResultForADifferentCurrencyIsNotShown() = runTest {
        val store = FakeStore().apply { saved = ApiJson.encodeToString(CachedResult(CachedKind.EXPLORE, ApiJson.encodeToString(exploreOk), 1000)) }
        val vm = vm(FakeRepo(), store, vmScope())
        vm.dispatch(SearchAction.SetOrigin("CNF")); vm.dispatch(SearchAction.SetCurrency("USD")); vm.dispatch(SearchAction.Submit); advanceUntilIdle()
        assertNull(vm.state.value.result)
    }
}
