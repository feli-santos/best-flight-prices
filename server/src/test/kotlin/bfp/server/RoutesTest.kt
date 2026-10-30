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
    private fun config(minApp: String = "0.0.0", rate: Int = 1000, deadlineMs: Long = 8000, trustProxy: Boolean = false) =
        ServerConfig(provider = "sample", token = null, marker = null, baseUrl = "x", ttlSeconds = 60, dailyBudget = 1000, ratePerMinute = rate, minAppVersion = minApp, requestDeadlineMs = deadlineMs, trustProxy = trustProxy)

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

    // ---- review hardening ----
    @Test fun unsupportedCurrencyIsRejectedBeforeTheProvider() = app {
        val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF", currency = "ZZZ")))
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals(ProblemTypes.CURRENCY_UNSUPPORTED, ApiJson.decodeFromString<Problem>(r.bodyAsText()).type)
    }

    @Test fun datesOutsideTheHorizonOrInvertedAreRejected() = app {
        fun rec(d: DatesDto) = ApiJson.encodeToString(RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "pt-lisbon"), d))
        for (d in listOf(DatesDto.Exact("1900-01-01", "1900-01-10"), DatesDto.Exact("9999-01-01", "9999-01-09"), DatesDto.Exact("2027-04-10", "2027-04-05"),
            DatesDto.Range("2030-01-01", "2030-02-01"), DatesDto.Exact("2027-04-10", "2027-07-20"))) {
            assertEquals(HttpStatusCode.BadRequest, client.postJson(ApiPaths.RECOMMENDATIONS, rec(d)).status, d.toString())
        }
        assertEquals(HttpStatusCode.BadRequest, client.postJson(ApiPaths.OPTIONS, ApiJson.encodeToString(OptionsRequest("CNF", "pt-lisbon", "1900-01"))).status)
        assertEquals(HttpStatusCode.BadRequest, client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF", DatesDto.Exact("9999-01-01")))).status)
    }

    @Test fun exactReturnDateDefinesTheStayAndOverridesAuto() = app {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "jp-tokyo"), DatesDto.Exact("2027-04-10", "2027-04-17"), StayDto.Auto)
        val p = ApiJson.decodeFromString<RecommendationResponse>(client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req)).bodyAsText()).places.single()
        assertEquals(7, p.stay.nights)
    }

    @Test fun rangeRecommendationsOnlyShowFaresInsideTheRange() = app {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "pt-lisbon"), DatesDto.Range("2027-01-20", "2027-03-02"))
        val p = ApiJson.decodeFromString<RecommendationResponse>(client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req)).bodyAsText()).places.single()
        assertEquals(listOf("2027-01", "2027-02", "2027-03"), p.months.map { it.yearMonth })
        for (m in p.months) m.fare?.departDate?.let { d -> assertTrue(d >= "2027-01-20" && d <= "2027-03-02", "fare outside range: $d") }
    }

    private class CountingExplore(val inner: bfp.core.FareProvider) : bfp.core.FareProvider by inner {
        val months = java.util.Collections.synchronizedList(mutableListOf<bfp.core.YearMonth?>())
        override suspend fun cheapestByDestination(origin: String, depart: bfp.core.YearMonth?, currency: String): List<bfp.core.FareQuote> {
            months += depart; return inner.cheapestByDestination(origin, depart, currency)
        }
    }

    @Test fun rangeExploreQueriesEachMonthSoDestinationsAreNotDropped() {
        val counting = CountingExplore(SampleFareProvider(catalog) { now })
        app(provider = counting) {
            val r = client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CNF", DatesDto.Range("2027-01-10", "2027-03-20"), "BRL")))
            val e = ApiJson.decodeFromString<ExploreResponse>(r.bodyAsText())
            assertEquals(setOf(bfp.core.YearMonth(2027, 1), bfp.core.YearMonth(2027, 2), bfp.core.YearMonth(2027, 3)), counting.months.toSet())
            val withFare = e.countries.count { it.cities.first().fareStatus == FareStatus.OK }
            assertTrue(withFare >= 40, "withFare=$withFare")
            for (c in e.countries) c.cities.mapNotNull { it.fare?.departDate }.forEach { assertTrue(it >= "2027-01-10" && it <= "2027-03-20") }
        }
    }

    @Test fun originsOwnCityAndNearbyAirportsAreExcludedFromExplore() = app {
        val e = ApiJson.decodeFromString<ExploreResponse>(client.postJson(ApiPaths.EXPLORE, ApiJson.encodeToString(ExploreRequest("CGH"))).bodyAsText())
        assertTrue(e.countries.flatMap { it.cities }.none { it.cityId == "br-sao-paulo" })
    }

    @Test fun slowProviderYieldsPendingMonthsNotAnError() = app(
        provider = object : bfp.core.FareProvider by SampleFareProvider(catalog, { now }) {
            override suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange): Map<bfp.core.YearMonth, bfp.core.FareQuote> {
                kotlinx.coroutines.delay(5_000); return emptyMap()
            }
        },
        cfg = config(deadlineMs = 200),
    ) {
        val req = RecommendationRequest("CNF", ScopeDto(ScopeKind.PLACE, "pt-lisbon"))
        val r = client.postJson(ApiPaths.RECOMMENDATIONS, ApiJson.encodeToString(req))
        assertEquals(HttpStatusCode.OK, r.status)
        val p = ApiJson.decodeFromString<RecommendationResponse>(r.bodyAsText()).places.single()
        assertTrue(p.months.all { it.fareStatus == FareStatus.PENDING && it.fare == null })
    }

    @Test fun oversizedBodyIs413() = app {
        val r = client.postJson(ApiPaths.EXPLORE, """{"origin":"CNF","pad":"${"x".repeat(20_000)}"}""")
        assertEquals(HttpStatusCode.PayloadTooLarge, r.status)
    }

    @Test fun trustedProxyKeysRateLimitOnForwardedClient() = app(cfg = config(rate = 1, trustProxy = true)) {
        suspend fun hit(ip: String) = client.post(ApiPaths.EXPLORE) {
            contentType(ContentType.Application.Json); setBody(ApiJson.encodeToString(ExploreRequest("CNF"))); header("X-Forwarded-For", ip)
        }.status
        assertEquals(HttpStatusCode.OK, hit("1.1.1.1"))
        assertEquals(HttpStatusCode.OK, hit("2.2.2.2"))          // different client, own bucket
        assertEquals(HttpStatusCode.TooManyRequests, hit("1.1.1.1"))
    }

    @Test fun forwardedHeadersAreIgnoredUnlessProxyIsTrusted() = app(cfg = config(rate = 1)) {
        suspend fun hit(ip: String) = client.post(ApiPaths.EXPLORE) {
            contentType(ContentType.Application.Json); setBody(ApiJson.encodeToString(ExploreRequest("CNF"))); header("X-Forwarded-For", ip)
        }.status
        assertEquals(HttpStatusCode.OK, hit("1.1.1.1"))
        assertEquals(HttpStatusCode.TooManyRequests, hit("2.2.2.2")) // spoofing does not escape the limit
    }
}
