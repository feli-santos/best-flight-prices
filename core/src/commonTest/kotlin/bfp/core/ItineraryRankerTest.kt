package bfp.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ItineraryRankerTest {
    private fun t(id: String, price: Long, dur: Int?, stops: Int, day: Int = 10) =
        Itinerary(id, price, dur, stops, LocalDate(2027, 4, day))

    @Test fun cheapestWinsWhenOtherwiseEqual() {
        val r = ItineraryRanker.rank(listOf(t("b", 2000, 900, 1), t("a", 1000, 900, 1)))
        assertEquals(listOf("a", "b"), r.map { it.id })
    }

    @Test fun priceDominatesButShortFlightCanWinWhenCloseInPrice() {
        val r = ItineraryRanker.rank(listOf(t("slow", 1000, 2000, 2), t("fast", 1050, 800, 0)))
        assertEquals("fast", r.first().id)
    }

    @Test fun tieBreakPriceThenDepartureDate() {
        val r = ItineraryRanker.rank(listOf(t("late", 1000, 900, 1, day = 20), t("early", 1000, 900, 1, day = 12)))
        assertEquals(listOf("early", "late"), r.map { it.id })
    }

    @Test fun missingDurationUsesMedian() {
        val r = ItineraryRanker.rank(listOf(t("a", 1000, 600, 0), t("b", 1000, null, 0), t("c", 1000, 1200, 0)))
        assertEquals("a", r.first().id)
        assertEquals("c", r.last().id)
    }

    @Test fun capsAtTwenty() =
        assertEquals(20, ItineraryRanker.rank((1..30).map { t("i$it", 1000L + it, 900, 1) }).size)

    @Test fun emptyAndSingle() {
        assertEquals(0, ItineraryRanker.rank(emptyList()).size)
        assertEquals(1, ItineraryRanker.rank(listOf(t("x", 1, null, 0))).size)
    }
}
