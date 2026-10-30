package bfp.server

import bfp.core.FareProvider
import bfp.core.FareQuote
import bfp.core.ProviderException
import bfp.core.YearMonth
import bfp.server.provider.GuardedProvider
import bfp.server.provider.ProviderBudget
import bfp.server.provider.TtlCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ControlsTest {
    @Test fun cacheHitsWithinTtlAndExpiresAfter() = runTest {
        var now = 0L
        val cache = TtlCache<String, Int>(ttlMs = 1000, nowMs = { now })
        val loads = AtomicInteger()
        suspend fun get() = cache.get("k") { loads.incrementAndGet() }
        assertEquals(1, get()); assertEquals(1, get())
        now = 1001
        assertEquals(2, get())
    }

    @Test fun singleFlightCoalescesConcurrentLoads() = runTest {
        val cache = TtlCache<String, Int>(ttlMs = 10_000, nowMs = { 0 })
        val loads = AtomicInteger()
        val r = (1..5).map { async { cache.get("k") { kotlinx.coroutines.yield(); loads.incrementAndGet() } } }.awaitAll()
        assertEquals(1, loads.get()); assertEquals(List(5) { 1 }, r)
    }

    @Test fun failuresAreNotCached() = runTest {
        val cache = TtlCache<String, Int>(ttlMs = 10_000, nowMs = { 0 })
        val n = AtomicInteger()
        assertFailsWith<IllegalStateException> { cache.get("k") { n.incrementAndGet(); error("x") } }
        assertEquals(2, cache.get("k") { n.incrementAndGet() })
    }

    @Test fun budgetCountsPerDayAndResets() {
        var day = 100L
        val b = ProviderBudget(limitPerDay = 2, dayProvider = { day })
        assertEquals(true, b.tryAcquire()); assertEquals(true, b.tryAcquire()); assertEquals(false, b.tryAcquire())
        day = 101
        assertEquals(true, b.tryAcquire())
    }

    private class Counting : FareProvider {
        val calls = AtomicInteger()
        override val name = "c"; override val isSample = false
        override suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange): Map<YearMonth, FareQuote> { calls.incrementAndGet(); return emptyMap() }
        override suspend fun roundTrips(origin: String, destination: String, depart: YearMonth, returnMonth: YearMonth, currency: String) = emptyList<FareQuote>()
        override suspend fun cheapestByDestination(origin: String, depart: YearMonth?, currency: String) = emptyList<FareQuote>()
    }

    @Test fun guardedProviderCachesAndEnforcesBudget() = runTest {
        val inner = Counting()
        val g = GuardedProvider(inner, ttlMs = 10_000, budget = ProviderBudget(2) { 1L }, nowMs = { 0 })
        g.monthlyCheapest("A", "B", "USD", 3..7); g.monthlyCheapest("A", "B", "USD", 3..7)
        assertEquals(1, inner.calls.get())
        g.monthlyCheapest("A", "C", "USD", 3..7)
        val e = assertFailsWith<ProviderException> { g.monthlyCheapest("A", "D", "USD", 3..7) }
        assertEquals(ProviderException.Kind.BUDGET_EXHAUSTED, e.kind)
    }
}
