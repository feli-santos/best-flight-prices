package bfp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StayAdvisorTest {
    private val cnf = GeoPoint(-19.6244, -43.9719)
    private val gru = GeoPoint(-23.4356, -46.4731)
    private val eze = GeoPoint(-34.8222, -58.5358)
    private val lis = GeoPoint(38.7742, -9.1342)
    private val tokyo = GeoPoint(35.5494, 139.7798)
    private val sydney = GeoPoint(-33.9399, 151.1753)

    private fun nights(to: GeoPoint, tzShift: Double, min: Int? = null) =
        StayAdvisor.adviseForRoute(cnf, to, tzShiftHours = tzShift, destinationMinNights = min).nights

    @Test fun domesticShortHaulIsThreeNights() = assertEquals(3, nights(gru, 0.0))
    @Test fun nearbyInternationalIsThreeNights() = assertEquals(3, nights(eze, 0.0))
    @Test fun lisbonIsSevenNights() = assertEquals(7, nights(lis, 3.0))
    @Test fun tokyoIsAtLeastTenNights() = assertTrue(nights(tokyo, 12.0) >= 10)
    @Test fun sydneyIsAtLeastTwelveNights() = assertTrue(nights(sydney, 14.0) >= 12)

    @Test fun timeZoneShiftOfEightHoursAddsANight() {
        val without = StayAdvisor.advise(journeyHours = 10.0, tzShiftHours = 7.0, destinationMinNights = null).nights
        val with = StayAdvisor.advise(journeyHours = 10.0, tzShiftHours = 8.0, destinationMinNights = null).nights
        assertEquals(without + 1, with)
    }

    @Test fun destinationMinimumApplies() =
        assertEquals(10, StayAdvisor.advise(2.0, 0.0, destinationMinNights = 10).nights)

    @Test fun clampedToTwentyOne() =
        assertEquals(21, StayAdvisor.advise(40.0, 12.0, destinationMinNights = 30).nights)

    @Test fun candidatesAreBaseMinusTwoBasePlusTwoPlusFourClampedAndDistinct() {
        val a = StayAdvisor.advise(10.0, 0.0, null) // base 7
        assertEquals(listOf(5, 7, 9, 11), a.candidates)
        val short = StayAdvisor.advise(1.0, 0.0, null) // base 3 -> 1 clamps to 2
        assertEquals(listOf(2, 3, 5, 7), short.candidates)
    }

    @Test fun baseNightsBoundaries() {
        assertEquals(3, StayAdvisor.advise(4.0, 0.0, null).nights)
        assertEquals(5, StayAdvisor.advise(4.1, 0.0, null).nights)
        assertEquals(5, StayAdvisor.advise(8.0, 0.0, null).nights)
        assertEquals(7, StayAdvisor.advise(14.0, 0.0, null).nights)
        assertEquals(10, StayAdvisor.advise(22.0, 0.0, null).nights)
        assertEquals(12, StayAdvisor.advise(22.1, 0.0, null).nights)
    }

    @Test fun distanceCnfToGruIsAbout500Km() {
        val d = distanceKm(cnf, gru)
        assertTrue(d in 400.0..600.0, "was $d")
    }

    @Test fun longStaysCarryReasonCodes() {
        val a = StayAdvisor.advise(26.0, 12.0, null)
        assertTrue(Reason(ReasonCode.STAY_LONG_HAUL) in a.reasons)
        assertTrue(Reason(ReasonCode.STAY_TIME_ZONE) in a.reasons)
    }
}
