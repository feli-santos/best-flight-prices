package bfp.server.provider

import bfp.core.FareProvider
import bfp.core.FareQuote
import bfp.core.FareSourceKind
import bfp.core.YearMonth
import bfp.core.distanceKm
import bfp.server.data.Catalog
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI

/**
 * Deterministic, clearly-labelled sample fares for development and tests. NOT real prices.
 * Price model: base + distance factor, seasonal swing, stable per-route jitter. Currency is nominal.
 */
class SampleFareProvider(private val catalog: Catalog, private val nowMs: () -> Long) : FareProvider {
    override val name = "sample"
    override val isSample = true

    private fun hash(s: String): Int { var h = 7; for (c in s) h = h * 31 + c.code; return abs(h) }

    private fun endpointOf(code: String) =
        catalog.originInfo(code)?.first ?: catalog.cityByGateway(code)?.point

    private fun priceMinor(origin: String, dest: String, ym: YearMonth, nights: Int): Long? {
        val a = endpointOf(origin) ?: return null
        val b = endpointOf(dest) ?: return null
        val km = distanceKm(a, b)
        val routeJitter = 0.85 + (hash("$origin-$dest") % 31) / 100.0
        val season = 1.0 + 0.25 * cos((ym.month - 1) / 12.0 * 2 * PI + (hash(dest) % 12))
        val base = 250.0 + km * 0.42 + nights * 12.0
        return (base * routeJitter * season * 100).toLong()
    }

    private fun quote(origin: String, dest: String, ym: YearMonth, nights: Int, deep: Boolean): FareQuote? {
        val price = priceMinor(origin, dest, ym, nights) ?: return null
        val dayOfMonth = 1 + hash("$origin$dest$ym") % 20
        val depart = LocalDate(ym.year, ym.month, dayOfMonth)
        val ret = LocalDate.fromEpochDays(depart.toEpochDays() + nights)
        val km = distanceKm(endpointOf(origin)!!, endpointOf(dest)!!)
        val transfers = if (km < 2500) 0 else if (km < 9000) 1 else 2
        return FareQuote(
            origin = origin, destination = dest, destinationAirport = null, departDate = depart, returnDate = ret,
            priceMinor = price, currency = "SAMPLE", transfers = transfers, returnTransfers = transfers,
            durationMinutes = (km / 800.0 * 60).toInt() + 60 + transfers * 150, airline = null, deepLink = null,
            retrievedAtEpochMs = nowMs(), source = FareSourceKind.SAMPLE,
        )
    }

    private fun months(from: YearMonth, count: Int) = generateSequence(from) { it.next() }.take(count).toList()

    private val horizonStart: YearMonth get() {
        val d = java.time.Instant.ofEpochMilli(nowMs()).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return YearMonth(d.year, d.monthValue).next()
    }

    private fun withCurrency(q: FareQuote, currency: String) = q.copy(currency = currency)

    override suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange): Map<YearMonth, FareQuote> {
        val nights = (tripDays.first + tripDays.last) / 2
        return months(horizonStart, 12).mapNotNull { ym -> quote(origin, destination, ym, nights, false)?.let { ym to withCurrency(it, currency) } }.toMap()
    }

    override suspend fun roundTrips(origin: String, destination: String, depart: YearMonth, returnMonth: YearMonth, currency: String): List<FareQuote> =
        listOf(5, 7, 9, 10, 12, 14).mapNotNull { n ->
            quote(origin, destination, depart, n, true)?.let { q ->
                val shifted = q.copy(departDate = LocalDate(depart.year, depart.month, 1 + (hash("$destination$n") % 24)))
                withCurrency(shifted.copy(returnDate = LocalDate.fromEpochDays(shifted.departDate.toEpochDays() + n), priceMinor = q.priceMinor + (hash("$n$destination") % 15000)), currency)
            }
        }

    override suspend fun cheapestByDestination(origin: String, depart: YearMonth?, currency: String): List<FareQuote> {
        val originCode = origin
        val ym = depart ?: horizonStart
        return catalog.cities.values.mapNotNull { c ->
            if (c.gateway == originCode) null
            else quote(originCode, c.gateway, ym, 9, false)?.let { withCurrency(it.copy(destinationAirport = c.airport), currency) }
        }
    }
}
