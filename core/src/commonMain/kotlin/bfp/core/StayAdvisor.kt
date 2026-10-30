package bfp.core

data class StayAdvice(val nights: Int, val candidates: List<Int>, val reasons: List<Reason>)

/** Suggests a cost-effective stay length from journey time, time-zone shift and destination minimums. */
object StayAdvisor {
    const val MIN_NIGHTS = 2
    const val MAX_NIGHTS = 21
    private const val HOURS_PER_CONNECTION = 2.5

    fun baseNights(journeyHours: Double): Int = when {
        journeyHours <= 4.0 -> 3
        journeyHours <= 8.0 -> 5
        journeyHours <= 14.0 -> 7
        journeyHours <= 22.0 -> 10
        else -> 12
    }

    fun advise(journeyHours: Double, tzShiftHours: Double, destinationMinNights: Int?): StayAdvice {
        val reasons = mutableListOf<Reason>()
        var n = baseNights(journeyHours)
        if (journeyHours > 14.0) reasons += Reason(ReasonCode.STAY_LONG_HAUL)
        if (kotlin.math.abs(tzShiftHours) >= 8.0) { n += 1; reasons += Reason(ReasonCode.STAY_TIME_ZONE) }
        if (destinationMinNights != null && destinationMinNights > n) {
            n = destinationMinNights; reasons += Reason(ReasonCode.STAY_DESTINATION_MIN)
        }
        n = n.coerceIn(MIN_NIGHTS, MAX_NIGHTS)
        val candidates = listOf(n - 2, n, n + 2, n + 4).map { it.coerceIn(MIN_NIGHTS, MAX_NIGHTS) }.distinct()
        return StayAdvice(n, candidates, reasons)
    }

    /** Estimates journey hours when the provider gives no duration. */
    fun estimateJourneyHours(from: GeoPoint, to: GeoPoint): Double {
        val km = distanceKm(from, to)
        val connections = when { km < 3000 -> 0; km < 9000 -> 1; else -> 2 }
        return km / 800.0 + 0.5 + HOURS_PER_CONNECTION * connections
    }

    fun adviseForRoute(from: GeoPoint, to: GeoPoint, tzShiftHours: Double, destinationMinNights: Int?): StayAdvice =
        advise(estimateJourneyHours(from, to), tzShiftHours, destinationMinNights)
}
