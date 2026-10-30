package bfp.server

import bfp.contracts.*
import bfp.core.ProviderException
import bfp.server.provider.SampleFareProvider
import bfp.server.data.Catalog
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class RoutesTest {
    private val catalog = Catalog.load()
    // 2026-10-30 12:00 UTC
    private val now = 1_793_361_600_000L
    private fun config(minApp: String = "0.0.0", rate: Int = 1000) =
        ServerConfig(provider = "sample", token = null, marker = null, baseUrl = "x", ttlSeconds = 60, dailyBudget = 1000, ratePerMinute = rate, minAppVersion = minApp)

    private fun app(provider: bfp.core.FareProvider = SampleFareProvider(catalog) { now }, cfg: ServerConfig = config(), block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication { application { module(cfg, catalog, provider) { now } }; block() }

    private suspend fun io.ktor.client.HttpClient.postJson(path: String, body: String, version: String? = "1.0.0") =
        post(path) { contentType(ContentType.Application.Json); setBody(body); if (version != null) header(ApiPaths.APP_VERSION_HEADER, version) }

    @Test fun health() = app {
        val r = client.get(ApiPaths.HEALTH)
        assertEquals(HttpStatusCode.OK, r.status)
        val h = ApiJson.decodeFromString<HealthResponse>(r.bodyAsText())
        assertEquals("ok", h.status); assertTrue(h.sample)
    }

    @Test fun exploreFromCnfReturnsTop50CountriesCheapestFirst() = app {
        val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF", currency = "BRL")))
        assertEquals(HttpStatusCode.OK, r.status)
        val e = ApiJson.decodeFromString<ExploreResponse>(r.bodyAsText())
        assertTrue(e.sample)
        assertTrue(e.countries.size in 45..50, "countries=${e.countries.size}")
        assertTrue(e.countries.all { it.cities.size in 1..3 })
        val prices = e.countries.mapNotNull { it.cities.first().fare?.price?.amount?.toBigDecimal() }
        assertEquals(prices.sorted(), prices)
        val br = e.countries.single { it.countryCode == "BR" }
        assertTrue(br.domestic)
        assertTrue(e.countries.flatMap { it.cities }.none { it.cityId == "br-belo-horizonte" })
        assertTrue(e.countries.all { it.bestMonths.size in 1..3 })
        assertEquals("BRL", e.currency)
    }

    @Test fun unknownOriginIs400Problem() = app {
        val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("ZZZ")))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.headers["Content-Type"]!!.startsWith("application/problem+json"))
        assertEquals(ProblemTypes.UNKNOWN_ORIGIN, ApiJson.decodeFromString<Problem>(r.bodyAsText()).type)
    }

    @Test fun malformedBodyIs400() = app {
        val r = client.postJson(ApiPaths.EXPLORE, "{nope")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals(ProblemTypes.INVALID_REQUEST, ApiJson.decodeFromString<Problem>(r.bodyAsText()).type)
    }

    @Test fun japanFlexibleAutoGivesTwelveMonthsAndLongStay() = app {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "jp-tokyo"), DatesDto.Anytime, StayDto.Auto, "BRL")
        val r = client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req))
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        val p = ApiJson.decodeFromString<RecommendationResponse>(r.bodyAsText()).places.single()
        assertEquals(12, p.months.size)
        assertTrue(p.stay.nights >= 10, "nights=${p.stay.nights}")
        assertTrue(p.months.all { it.reasons.size <= 2 })
        assertEquals(p.months.map { it.yearMonth }.sorted(), p.months.map { it.yearMonth })
        assertTrue(p.months.any { it.band == BandDto.GREAT || it.band == BandDto.GOOD })
        assertNotNull(p.cheapest)
    }

    @Test fun uyuniMirrorSeasonIsSurfacedInJanToMar() = app {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "bo-uyuni"))
        val p = ApiJson.decodeFromString<RecommendationResponse>(client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req)).bodyAsText()).places.single()
        val mirror = p.months.filter { it.yearMonth.takeLast(2) in listOf("01", "02", "03") }
        assertTrue(mirror.all { m -> m.reasons.any { it.code == ReasonCodeDto.EXPERIENCE && it.arg == "uyuni-mirror" } })
    }

    @Test fun countryScopeReturnsItsCitiesRankedAndFixedNightsAreHonoured() = app {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.COUNTRY, "JP"), StayDto.Nights(14).let { DatesDto.Anytime }, StayDto.Nights(14))
        val r = ApiJson.decodeFromString<RecommendationResponse>(client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req)).bodyAsText())
        assertTrue(r.places.size in 2..3)
        assertTrue(r.places.all { it.stay.nights == 14 })
        val cheap = r.places.mapNotNull { it.cheapest?.price?.amount?.toBigDecimal() }
        assertEquals(cheap.sorted(), cheap)
    }

    @Test fun exactDatesNarrowToOneMonth() = app {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "pt-lisbon"), DatesDto.Exact("2027-04-10", "2027-04-20"))
        val p = ApiJson.decodeFromString<RecommendationResponse>(client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req)).bodyAsText()).places.single()
        assertEquals(listOf("2027-04"), p.months.map { it.yearMonth })
    }

    @Test fun unknownDestinationIs400() = app {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "nowhere"))
        val r = client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals(ProblemTypes.UNKNOWN_DESTINATION, ApiJson.decodeFromString<Problem>(r.bodyAsText()).type)
    }

    @Test fun optionsAreRankedAndMatchStay() = app {
        val req = OptionsRequest("CNF", "pt-lisbon", "2027-04", nights = 7, currency = "BRL")
        val r = ApiJson.decodeFromString<OptionsResponse>(client.postJson(ApiPaths.OPTIONS, ApiJson.encodeToString(req)).bodyAsText())
        assertTrue(r.options.isNotEmpty() && r.options.size <= 20)
        assertTrue(r.options.all { kotlin.math.abs(it.nights - 7) <= 1 })
    }

    @Test fun oldAppVersionGets426() = app(cfg = config(minApp = "2.0.0")) {
        val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF")), version = "1.9.9")
        assertEquals(HttpStatusCode.UpgradeRequired, r.status)
        assertEquals(ProblemTypes.UPDATE_REQUIRED, ApiJson.decodeFromString<Problem>(r.bodyAsText()).type)
    }

    @Test fun healthIsExemptFromVersionGate() = app(cfg = config(minApp = "2.0.0")) {
        assertEquals(HttpStatusCode.OK, client.get(ApiPaths.HEALTH).status)
    }

    @Test fun rateLimitReturns429Problem() = app(cfg = config(rate = 2)) {
        repeat(2) { client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF"))) }
        val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF")))
        assertEquals(HttpStatusCode.TooManyRequests, r.status)
        assertEquals(ProblemTypes.RATE_LIMITED, ApiJson.decodeFromString<Problem>(r.bodyAsText()).type)
    }

    private class Failing(private val kind: ProviderException.Kind) : bfp.core.FareProvider {
        override val name = "f"; override val isSample = false
        override suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange): Map<bfp.core.YearMonth, bfp.core.FareQuote> = throw ProviderException(kind, "x", 9)
        override suspend fun roundTrips(origin: String, destination: String, depart: bfp.core.YearMonth, returnMonth: bfp.core.YearMonth, currency: String): List<bfp.core.FareQuote> = throw ProviderException(kind, "x", 9)
        override suspend fun cheapestByDestination(origin: String, depart: bfp.core.YearMonth?, currency: String): List<bfp.core.FareQuote> = throw ProviderException(kind, "x", 9)
    }

    @Test fun providerUnauthorizedIs502WithTypedProblem() = app(provider = Failing(ProviderException.Kind.UNAUTHORIZED)) {
        val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF")))
        assertEquals(HttpStatusCode.BadGateway, r.status)
        assertEquals(ProblemTypes.PROVIDER_UNAUTHORIZED, ApiJson.decodeFromString<Problem>(r.bodyAsText()).type)
    }

    @Test fun providerRateLimitedIs503WithRetryAfter() = app(provider = Failing(ProviderException.Kind.RATE_LIMITED)) {
        val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF")))
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
        assertEquals(9, ApiJson.decodeFromString<Problem>(r.bodyAsText()).retryAfterSeconds)
    }

    @Test fun recommendationsWithFailingProviderReportMissingNotInvented() = app(provider = Failing(ProviderException.Kind.UNAVAILABLE)) {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "pt-lisbon"))
        val r = client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req))
        assertEquals(HttpStatusCode.ServiceUnavailable, r.status)
    }

    @Test fun partialFaresMarkMonthsMissing() = app(provider = object : bfp.core.FareProvider by SampleFareProvider(catalog, { now }) {
        override suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange) =
            SampleFareProvider(catalog) { now }.monthlyCheapest(origin, destination, currency, tripDays).entries.take(5).associate { it.key to it.value }
    }) {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "pt-lisbon"))
        val p = ApiJson.decodeFromString<RecommendationResponse>(client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req)).bodyAsText()).places.single()
        assertEquals(12, p.months.size)
        val missing = p.months.filter { it.fareStatus == FareStatus.MISSING }
        assertEquals(7, missing.size)
        assertTrue(missing.all { it.fare == null && it.reasons.any { r -> r.code == ReasonCodeDto.FARE_MISSING } && it.confidence != ConfidenceDto.HIGH })
    }
}
