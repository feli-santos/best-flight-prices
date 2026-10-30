package bfp.contracts

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Freezes the wire format. Changing these strings is a breaking contract change. */
class ContractsGoldenTest {
    private val fare = FareDto(
        price = MoneyDto("1234.50", "BRL"), retrievedAt = "2026-10-30T12:00:00Z", source = FareSource.SAMPLE,
        deepLink = "/search/X", transfers = 1,
    )

    @Test fun exploreRequestGolden() {
        val json = ApiJson.encodeToString(ExploreRequest("CNF", DatesDto.Range("2027-01-01", "2027-03-31"), "BRL"))
        assertEquals(
            """{"origin":"CNF","dates":{"type":"range","from":"2027-01-01","to":"2027-03-31"},"currency":"BRL"}""", json,
        )
    }

    @Test fun anytimeAndAutoGolden() {
        assertEquals("""{"type":"anytime"}""", ApiJson.encodeToString<DatesDto>(DatesDto.Anytime))
        assertEquals("""{"type":"auto"}""", ApiJson.encodeToString<StayDto>(StayDto.Auto))
        assertEquals("""{"type":"nights","value":9}""", ApiJson.encodeToString<StayDto>(StayDto.Nights(9)))
    }

    @Test fun fareGoldenOmitsNulls() {
        assertEquals(
            """{"price":{"amount":"1234.50","currency":"BRL"},"retrievedAt":"2026-10-30T12:00:00Z","source":"SAMPLE","deepLink":"/search/X","transfers":1}""",
            ApiJson.encodeToString(fare),
        )
    }

    @Test fun clientToleratesUnknownFields() {
        val parsed = ApiJson.decodeFromString<MoneyDto>("""{"amount":"1","currency":"USD","future":"x"}""")
        assertEquals("USD", parsed.currency)
    }

    @Test fun requestDefaultsApply() {
        val r = ApiJson.decodeFromString<RecommendationRequest>("""{"origin":"CNF","scope":{"kind":"COUNTRY","id":"JP"}}""")
        assertEquals(DatesDto.Anytime, r.dates)
        assertEquals(StayDto.Auto, r.stay)
        assertEquals("USD", r.currency)
    }

    @Test fun reasonCodesMatchCoreEnum() {
        // Server maps core ReasonCode -> ReasonCodeDto by name; keep names identical.
        val core = bfp.core.ReasonCode.entries.map { it.name }.toSet()
        val dto = ReasonCodeDto.entries.map { it.name }.toSet()
        assertEquals(core, dto)
    }

    @Test fun roundTripResponse() {
        val r = ExploreResponse("CNF", listOf(ExploreCountryResult("PT", false, listOf(CityResult("pt-lisbon", fare, FareStatus.OK)), listOf(5, 6))), "BRL", true, "2026-10-30T12:00:00Z")
        assertEquals(r, ApiJson.decodeFromString(ApiJson.encodeToString(r)))
    }

    @Test fun problemGolden() {
        val p = Problem(ProblemTypes.RATE_LIMITED, "Too many requests", 429, retryAfterSeconds = 12)
        assertTrue(ApiJson.encodeToString(p).contains(""""retryAfterSeconds":12"""))
    }
}
