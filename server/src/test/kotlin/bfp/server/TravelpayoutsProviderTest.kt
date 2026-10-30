package bfp.server

import bfp.core.ProviderException
import bfp.core.YearMonth
import bfp.server.provider.TravelpayoutsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TravelpayoutsProviderTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun provider(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
        extraHeaders: io.ktor.http.Headers = json,
        capture: MutableList<io.ktor.client.request.HttpRequestData> = mutableListOf(),
    ): TravelpayoutsProvider {
        val engine = MockEngine { req -> capture += req; respond(body, status, extraHeaders) }
        return TravelpayoutsProvider(HttpClient(engine), "https://api.example", "SECRET-TOKEN", "BHZ-marker", { 1_800_000_000_000L })
    }

    private val grouped = """{"success":true,"data":{
      "2027-04-10":{"origin":"BHZ","destination":"LIS","origin_airport":"CNF","destination_airport":"LIS","price":3890.5,
        "airline":"TP","flight_number":"90","departure_at":"2027-04-10T22:00:00-03:00","return_at":"2027-04-20T10:00:00+01:00",
        "transfers":0,"return_transfers":1,"duration":1500,"link":"/search/BHZ1004LIS20041?t=abc"},
      "2027-05-02":{"origin":"BHZ","destination":"LIS","price":4100,"departure_at":"2027-05-02T22:00:00-03:00","return_at":"2027-05-12T10:00:00+01:00","transfers":1,"return_transfers":1,"duration":1700}
    },"currency":"brl"}"""

    @Test fun parsesGroupedPricesIntoMonths() = runTest {
        val r = provider(body = grouped).monthlyCheapest("BHZ", "LIS", "BRL", 5..14)
        assertEquals(2, r.size)
        val april = r[YearMonth(2027, 4)]!!
        assertEquals(389050L, april.priceMinor)
        assertEquals("BRL", april.currency)
        assertEquals(0, april.transfers)
        assertEquals(1500, april.durationMinutes)
        assertEquals("TP", april.airline)
        assertEquals("https://www.aviasales.com/search/BHZ1004LIS20041?t=abc&marker=BHZ-marker", april.deepLink)
        assertNull(r[YearMonth(2027, 5)]!!.deepLink)
    }

    @Test fun tokenGoesInHeaderNeverInUrl() = runTest {
        val cap = mutableListOf<io.ktor.client.request.HttpRequestData>()
        provider(body = grouped, capture = cap).monthlyCheapest("BHZ", "LIS", "BRL", 5..14)
        val req = cap.single()
        assertEquals("SECRET-TOKEN", req.headers["X-Access-Token"])
        assertTrue(!req.url.toString().contains("SECRET-TOKEN"))
        assertEquals("/aviasales/v3/grouped_prices", req.url.encodedPath)
        val q = req.url.parameters
        assertEquals("month", q["group_by"]); assertEquals("brl", q["currency"])
        assertEquals("5", q["min_trip_duration"]); assertEquals("14", q["max_trip_duration"])
    }

    @Test fun successFalseIsBadResponse() = runTest {
        val e = assertFailsWith<ProviderException> { provider(body = """{"success":false,"error":"bad"}""").monthlyCheapest("A", "B", "BRL", 3..7) }
        assertEquals(ProviderException.Kind.BAD_RESPONSE, e.kind)
    }

    @Test fun malformedJsonIsBadResponse() = runTest {
        val e = assertFailsWith<ProviderException> { provider(body = "<html>").monthlyCheapest("A", "B", "BRL", 3..7) }
        assertEquals(ProviderException.Kind.BAD_RESPONSE, e.kind)
    }

    @Test fun mapsStatusCodes() = runTest {
        assertEquals(ProviderException.Kind.UNAUTHORIZED,
            assertFailsWith<ProviderException> { provider(HttpStatusCode.Unauthorized, "{}").monthlyCheapest("A", "B", "BRL", 3..7) }.kind)
        val rl = assertFailsWith<ProviderException> {
            provider(HttpStatusCode.TooManyRequests, "{}", headersOf("X-Rate-Limit-Reset", "7")).monthlyCheapest("A", "B", "BRL", 3..7)
        }
        assertEquals(ProviderException.Kind.RATE_LIMITED, rl.kind)
        assertEquals(7, rl.retryAfterSeconds)
        assertEquals(ProviderException.Kind.UNAVAILABLE,
            assertFailsWith<ProviderException> { provider(HttpStatusCode.InternalServerError, "{}").monthlyCheapest("A", "B", "BRL", 3..7) }.kind)
    }

    @Test fun emptyDataIsEmptyResultNotError() = runTest {
        assertTrue(provider(body = """{"success":true,"data":{}}""").monthlyCheapest("A", "B", "BRL", 3..7).isEmpty())
        assertTrue(provider(body = """{"success":true,"data":[]}""").roundTrips("A", "B", YearMonth(2027, 4), YearMonth(2027, 4), "BRL").isEmpty())
    }

    @Test fun roundTripsUseListEndpoint() = runTest {
        val cap = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val body = """{"success":true,"data":[{"origin":"BHZ","destination":"LIS","price":4000,"departure_at":"2027-04-10T22:00:00-03:00","return_at":"2027-04-20T10:00:00+01:00","transfers":1,"return_transfers":0,"duration":1600,"link":"/search/x"}],"currency":"brl"}"""
        val r = provider(body = body, capture = cap).roundTrips("BHZ", "LIS", YearMonth(2027, 4), YearMonth(2027, 4), "BRL")
        assertEquals(1, r.size)
        val q = cap.single().url.parameters
        assertEquals("false", q["one_way"]); assertEquals("2027-04", q["departure_at"]); assertEquals("2027-04", q["return_at"])
        assertEquals("/aviasales/v3/prices_for_dates", cap.single().url.encodedPath)
    }

    @Test fun cheapestByDestinationUsesOriginOnlyUnique() = runTest {
        val cap = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val body = """{"success":true,"data":[{"origin":"BHZ","destination":"LIS","destination_airport":"LIS","price":4000,"departure_at":"2027-04-10T22:00:00-03:00","return_at":"2027-04-20T10:00:00+01:00","transfers":1}],"currency":"brl"}"""
        val r = provider(body = body, capture = cap).cheapestByDestination("BHZ", null, "BRL")
        assertEquals("LIS", r.single().destination)
        val q = cap.single().url.parameters
        assertEquals("true", q["unique"]); assertEquals("false", q["one_way"]); assertNull(q["destination"])
    }

    @Test fun rejectsWrongCurrencyEcho() = runTest {
        val e = assertFailsWith<ProviderException> {
            provider(body = grouped.replace("\"currency\":\"brl\"", "\"currency\":\"rub\"")).monthlyCheapest("BHZ", "LIS", "BRL", 5..14)
        }
        assertEquals(ProviderException.Kind.CURRENCY_UNSUPPORTED, e.kind)
    }
}
