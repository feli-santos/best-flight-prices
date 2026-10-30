package bfp.server

import bfp.core.YearMonth
import bfp.server.data.Catalog
import bfp.server.provider.SampleFareProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleProviderTest {
    private val catalog = Catalog.load()
    private val provider = SampleFareProvider(catalog) { 1_800_000_000_000L }

    @Test fun isLabeledSample() {
        assertTrue(provider.isSample)
    }

    @Test fun deterministic() = runTest {
        val a = provider.monthlyCheapest("CNF", "LIS", "BRL", 5..14)
        val b = provider.monthlyCheapest("CNF", "LIS", "BRL", 5..14)
        assertEquals(a, b)
    }

    @Test fun farLongHaulCostsMoreThanShortHaul() = runTest {
        val m = YearMonth(2027, 4)
        val gru = provider.monthlyCheapest("CNF", "SAO", "BRL", 3..7)[m]!!.priceMinor
        val tyo = provider.monthlyCheapest("CNF", "TYO", "BRL", 10..14)[m]!!.priceMinor
        assertTrue(tyo > gru * 3, "tokyo=$tyo gru=$gru")
    }

    @Test fun coversTwelveMonthsAndMarksSource() = runTest {
        val r = provider.monthlyCheapest("CNF", "LIS", "BRL", 5..14)
        assertEquals(12, r.size)
        assertTrue(r.values.all { it.source == bfp.core.FareSourceKind.SAMPLE && it.deepLink == null })
    }

    @Test fun exploreCoversCatalogCities() = runTest {
        val r = provider.cheapestByDestination("CNF", null, "BRL")
        assertTrue(r.size > 100)
        assertTrue(r.none { it.destination == "BHZ" || it.destination == "CNF" })
    }

    @Test fun roundTripsAreSortedByVariationAndHaveDates() = runTest {
        val r = provider.roundTrips("CNF", "LIS", YearMonth(2027, 4), YearMonth(2027, 4), "BRL")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.returnDate!! > it.departDate })
    }
}
