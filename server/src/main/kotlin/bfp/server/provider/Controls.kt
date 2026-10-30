package bfp.server.provider

import bfp.core.FareProvider
import bfp.core.FareQuote
import bfp.core.ProviderException
import bfp.core.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.atomic.AtomicLong

/**
 * Async TTL cache with single-flight: concurrent loads for one key share one call, which runs in a
 * cache-owned scope so one caller's cancellation never fails other waiters. Failures are not cached.
 * State is guarded by a plain monitor (tiny critical sections, no suspension) so cleanup cannot be cancelled.
 */
class TtlCache<K : Any, V : Any>(private val ttlMs: Long, private val nowMs: () -> Long, private val maxEntries: Int = 5000) {
    private class Entry<V>(val value: V, val at: Long)
    private val done = LinkedHashMap<K, Entry<V>>()
    private val inflight = HashMap<K, Deferred<V>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun size(): Int = synchronized(this) { done.size }

    suspend fun get(key: K, load: suspend () -> V): V {
        val deferred = synchronized(this) {
            val hit = done[key]
            if (hit != null && nowMs() - hit.at < ttlMs) return hit.value
            inflight.getOrPut(key) {
                scope.async {
                    try {
                        val v = load()
                        synchronized(this@TtlCache) { store(key, v) }
                        v
                    } finally {
                        synchronized(this@TtlCache) { inflight.remove(key) }
                    }
                }
            }
        }
        return deferred.await()
    }

    private fun store(key: K, v: V) {
        val now = nowMs()
        done.entries.removeAll { now - it.value.at >= ttlMs }
        done[key] = Entry(v, now)
        while (done.size > maxEntries) done.remove(done.keys.first())
    }
}

/** Daily cap on real provider calls (protects free-tier limits and cost). */
class ProviderBudget(private val limitPerDay: Int, private val dayProvider: () -> Long) {
    private val day = AtomicLong(-1)
    private val used = java.util.concurrent.atomic.AtomicInteger(0)

    @Synchronized
    fun tryAcquire(): Boolean {
        val today = dayProvider()
        if (day.get() != today) { day.set(today); used.set(0) }
        return if (used.get() < limitPerDay) { used.incrementAndGet(); true } else false
    }
}

/** Wraps any provider with a shared TTL cache and the daily budget. Budget is consumed only on cache misses. */
class GuardedProvider(
    private val inner: FareProvider,
    ttlMs: Long,
    private val budget: ProviderBudget,
    nowMs: () -> Long,
) : FareProvider by inner {
    private val monthly = TtlCache<String, Map<YearMonth, FareQuote>>(ttlMs, nowMs)
    private val trips = TtlCache<String, List<FareQuote>>(ttlMs, nowMs)
    private val explore = TtlCache<String, List<FareQuote>>(ttlMs, nowMs, maxEntries = 200)

    private fun charge() { if (!budget.tryAcquire()) throw ProviderException(ProviderException.Kind.BUDGET_EXHAUSTED, "daily provider budget exhausted") }

    override suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange) =
        monthly.get("$origin|$destination|$currency|${tripDays.first}-${tripDays.last}") { charge(); inner.monthlyCheapest(origin, destination, currency, tripDays) }

    override suspend fun roundTrips(origin: String, destination: String, depart: YearMonth, returnMonth: YearMonth, currency: String) =
        trips.get("$origin|$destination|$depart|$returnMonth|$currency") { charge(); inner.roundTrips(origin, destination, depart, returnMonth, currency) }

    override suspend fun cheapestByDestination(origin: String, depart: YearMonth?, currency: String) =
        explore.get("$origin|$depart|$currency") { charge(); inner.cheapestByDestination(origin, depart, currency) }
}
