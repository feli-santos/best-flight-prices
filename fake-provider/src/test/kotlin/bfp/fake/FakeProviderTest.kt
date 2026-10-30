package bfp.fake

import bfp.core.ProviderException
import bfp.core.YearMonth
import bfp.server.data.Catalog
import bfp.server.provider.TravelpayoutsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Drives the REAL Travelpayouts adapter against the fake over real HTTP for every fault scenario. */
class FakeProviderTest {
    private val catalog = Catalog.load()
    private val engine = embeddedServer(Netty, port = 0) { fakeModule(catalog) { 1_793_361_600_000L } }.also { it.start(wait = false) }
    private val port = runBlocking { engine.engine.resolvedConnectors().first().port }
    private val base = "http://127.0.0.1:$port"
    private val http = HttpClient(CIO)
    private fun provider(token: String = "good") = TravelpayoutsProvider(http, base, token, null) { 1_793_361_600_000L }
    private suspend fun scenario(name: String) { http.post("$base/__scenario/$name") }

    @AfterTest fun stop() { http.close(); engine.stop(100, 500) }

    @Test fun normalReturnsMonthlyFares() = runTest {
        scenario("normal")
        val m = provider().monthlyCheapest("CNF", "LIS", "BRL", 5..14)
        assertEquals(12, m.size)
    }

    @Test fun exploreReturnsCityFares() = runTest {
        scenario("normal")
        assertTrue(provider().cheapestByDestination("CNF", null, "BRL").size > 100)
    }

    @Test fun roundTripsReturnList() = runTest {
        scenario("normal")
        assertTrue(provider().roundTrips("CNF", "LIS", YearMonth(2027, 4), YearMonth(2027, 4), "BRL").isNotEmpty())
    }

    private suspend fun kindOf(name: String): ProviderException.Kind {
        scenario(name)
        return assertFailsWith<ProviderException> { provider().monthlyCheapest("CNF", "LIS", "BRL", 5..14) }.kind
    }

    @Test fun faultsMapToProviderErrors() = runTest {
        assertEquals(ProviderException.Kind.RATE_LIMITED, kindOf("rate_limited"))
        assertEquals(ProviderException.Kind.UNAUTHORIZED, kindOf("unauthorized"))
        assertEquals(ProviderException.Kind.UNAVAILABLE, kindOf("server_error"))
        assertEquals(ProviderException.Kind.BAD_RESPONSE, kindOf("malformed"))
        assertEquals(ProviderException.Kind.BAD_RESPONSE, kindOf("success_false"))
        assertEquals(ProviderException.Kind.CURRENCY_UNSUPPORTED, kindOf("no_brl"))
    }

    @Test fun emptyAndPartialScenarios() = runTest {
        scenario("empty")
        assertTrue(provider().monthlyCheapest("CNF", "LIS", "BRL", 5..14).isEmpty())
        scenario("partial")
        val n = provider().monthlyCheapest("CNF", "LIS", "BRL", 5..14).size
        assertTrue(n in 1..11, "n=$n")
    }

    @Test fun badTokenIsRejectedInNormalMode() = runTest {
        scenario("normal")
        assertEquals(ProviderException.Kind.UNAUTHORIZED,
            assertFailsWith<ProviderException> { provider("").monthlyCheapest("CNF", "LIS", "BRL", 5..14) }.kind)
    }

    @Test fun scenarioEndpointValidatesNames() = runTest {
        assertEquals(400, http.post("$base/__scenario/not-a-scenario") { }.status.value)
        assertEquals(200, http.get("$base/__scenario").status.value)
    }
}
