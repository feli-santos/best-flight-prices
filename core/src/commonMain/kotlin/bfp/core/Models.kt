package bfp.core

import kotlinx.datetime.LocalDate

/** Month of a year. Comparable; formats as YYYY-MM. */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    init { require(month in 1..12) { "month must be 1..12" } }
    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
    override fun compareTo(other: YearMonth): Int = (year * 12 + month) - (other.year * 12 + other.month)
    override fun toString(): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}"

    companion object {
        fun parse(s: String): YearMonth {
            val parts = s.split("-")
            require(parts.size == 2) { "expected YYYY-MM" }
            return YearMonth(parts[0].toInt(), parts[1].toInt())
        }
        fun of(date: LocalDate) = YearMonth(date.year, date.monthNumber)
    }
}

data class GeoPoint(val lat: Double, val lon: Double)

enum class Band { GREAT, GOOD, MIXED, AVOID }
enum class Confidence { HIGH, MEDIUM, LOW }
enum class CycloneRisk { LOW, MODERATE, HIGH }
enum class ExperienceLevel { PEAK, SHOULDER, NONE }

/** Codes only; the server never produces prose. Clients map each to a localized string. */
enum class ReasonCode {
    FARE_CHEAPEST, FARE_LOW, FARE_HIGH, FARE_MISSING,
    CLIMATE_PLEASANT, CLIMATE_HOT, CLIMATE_COLD, CLIMATE_WET, CLIMATE_HUMID,
    CYCLONE_HIGH, CYCLONE_MODERATE,
    EXPERIENCE,
    STAY_LONG_HAUL, STAY_TIME_ZONE, STAY_DESTINATION_MIN,
}

data class Reason(val code: ReasonCode, val arg: String? = null)

data class ClimateMonth(val tMax: Double, val tMin: Double, val precipMmDay: Double, val humidity: Double)
data class Experience(val id: String, val level: ExperienceLevel)

sealed interface DateIntent {
    data object Flexible : DateIntent
    data class Range(val from: LocalDate, val to: LocalDate) : DateIntent {
        init { require(from <= to) { "range start must not be after end" } }
    }
    data class Exact(val depart: LocalDate, val ret: LocalDate? = null) : DateIntent
}
