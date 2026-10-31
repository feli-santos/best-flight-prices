package bfp.server

import bfp.client.*
import bfp.contracts.*
import bfp.server.data.Catalog
import bfp.server.provider.SampleFareProvider
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The real client against the real server routes: catches wire-contract drift that mocks cannot. */
class ClientContractTest {
    private val catalog = Catalog.load()
    private val now = 1_793_361_600_000L
    private fun cfg(minApp: String = "0.0.0") =
        ServerConfig(provider = "sample", token = null, marker = null, baseUrl = "x", ttlSeconds = 60, dailyBudget = 1000, ratePerMinute = 1000, minAppVersion = minApp, requestDeadlineMs = 8000, trustProxy = false)

    private fun withClient(version: String = "1.0.0", minApp: String = "0.0.0", block: suspend (ApiClient) -> Unit) = testApplication {
        application { module(cfg(minApp), catalog, SampleFareProvider(catalog) { now }) { now } }
        block(ApiClient(client, "http://localhost", version))
    }

    @Test fun exploreRoundTrip() = withClient { api ->
        val r = assertIs<ApiResult.Ok<ExploreResponse>>(api.explore(ExploreRequest("CNF", currency = "BRL"))).value
        assertTrue(r.sample); assertTrue(r.countries.size >= 45)
    }

    @Test fun recommendationsAndOptionsRoundTrip() = withClient { api ->
        val rec = assertIs<ApiResult.Ok<RecommendationResponse>>(api.recommendations(RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "pt-lisbon"), currency = "BRL"))).value
        val place = rec.places.single()
        val month = place.months.first { it.fare != null }
        val opts = assertIs<ApiResult.Ok<OptionsResponse>>(api.options(OptionsRequest("CNF", "pt-lisbon", month.yearMonth, place.stay.nights, "BRL"))).value
        assertTrue(opts.options.isNotEmpty())
    }

    @Test fun oldAppVersionMapsToUpdateRequired() = withClient(version = "0.0.1", minApp = "1.0.0") { api ->
        assertEquals(ApiFailure.UpdateRequired, assertIs<ApiResult.Failure>(api.explore(ExploreRequest("CNF"))).failure)
    }

    @Test fun badInputMapsToInvalidRequest() = withClient { api ->
        assertEquals(ApiFailure.InvalidRequest, assertIs<ApiResult.Failure>(api.explore(ExploreRequest("ZZZ"))).failure)
        assertEquals(ApiFailure.CurrencyUnsupported, assertIs<ApiResult.Failure>(api.explore(ExploreRequest("CNF", currency = "XXX"))).failure)
    }
}
