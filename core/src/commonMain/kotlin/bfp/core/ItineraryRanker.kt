package bfp.core

import kotlinx.datetime.LocalDate

data class Itinerary(
    val id: String,
    val priceMinor: Long,
    val durationMin: Int?,
    val stops: Int,
    val departDate: LocalDate,
)

/** Price 55%, total duration 25%, stops 20%. Lower combined penalty ranks first. */
object ItineraryRanker {
    const val MAX_RESULTS = 20

    fun rank(items: List<Itinerary>): List<Itinerary> {
        if (items.isEmpty()) return emptyList()
        val minPrice = items.minOf { it.priceMinor }.coerceAtLeast(1)
        val known = items.mapNotNull { it.durationMin }.sorted()
        val median = when {
            known.isEmpty() -> null
            known.size % 2 == 1 -> known[known.size / 2].toDouble()
            else -> (known[known.size / 2 - 1] + known[known.size / 2]) / 2.0
        }
        val minDur = known.firstOrNull()?.toDouble()

        fun penalty(it: Itinerary): Double {
            val price = ((it.priceMinor - minPrice).toDouble() / minPrice).coerceIn(0.0, 1.0)
            val dur = if (minDur == null || minDur <= 0) 0.0 else {
                val d = it.durationMin?.toDouble() ?: median!!
                ((d - minDur) / minDur).coerceIn(0.0, 1.0)
            }
            val stops = it.stops.coerceIn(0, 2) / 2.0
            return 0.55 * price + 0.25 * dur + 0.20 * stops
        }
        return items.sortedWith(
            compareBy<Itinerary> { penalty(it) }.thenBy { it.priceMinor }.thenBy { it.departDate }.thenBy { it.id },
        ).take(MAX_RESULTS)
    }
}
