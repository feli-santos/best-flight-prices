package bfp.core

import kotlinx.datetime.LocalDate

object DatePlanner {
    const val HORIZON_MONTHS = 12

    /** Months to evaluate: next 12 months (starting next month) for flexible; clamped overlap for ranges. */
    fun months(intent: DateIntent, today: LocalDate): List<YearMonth> {
        val first = YearMonth.of(today).next()
        val horizon = generateSequence(first) { it.next() }.take(HORIZON_MONTHS).toList()
        return when (intent) {
            DateIntent.Flexible -> horizon
            is DateIntent.Range -> {
                val from = YearMonth.of(intent.from)
                val to = YearMonth.of(intent.to)
                horizon.filter { it >= from && it <= to }
            }
            is DateIntent.Exact -> listOf(YearMonth.of(intent.depart))
        }
    }

    /** True when the stay is within one night of the requested length (or any stay when nights is null). */
    fun matchesStay(depart: LocalDate, ret: LocalDate, nights: Int?): Boolean {
        if (nights == null) return true
        val actual = (ret.toEpochDays() - depart.toEpochDays()).toInt()
        return kotlin.math.abs(actual - nights) <= 1
    }
}
