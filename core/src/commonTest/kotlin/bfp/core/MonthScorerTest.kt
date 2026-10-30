package bfp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthScorerTest {
    private val pleasant = ClimateMonth(tMax = 24.0, tMin = 16.0, precipMmDay = 1.0, humidity = 60.0)
    private fun input(
        fare: Long? = 1000, min: Long? = 1000, climate: ClimateMonth? = pleasant,
        cyclone: CycloneRisk? = CycloneRisk.LOW, exp: Experience? = null, age: Int? = 1,
    ) = MonthInputs(month = 3, fareMinor = fare, minFareMinor = min, climate = climate,
        cyclone = cyclone, experience = exp, fareAgeDays = age)

    @Test fun allGoodIsGreat() = assertEquals(Band.GREAT, MonthScorer.score(input()).band)

    @Test fun bandIsMonotonicInFare() {
        val bands = listOf(1000L, 1300L, 1700L, 2000L).map { MonthScorer.score(input(fare = it)).band.ordinal }
        assertEquals(bands.sorted(), bands) // ordinal: GREAT=0 .. AVOID=3, never improves as fare rises
    }

    @Test fun fareAtDoubleMinimumScoresZeroForThatFactor() {
        val s = MonthScorer.score(input(fare = 2000))
        assertEquals(0.0, s.factors.fare!!, 1e-9)
    }

    @Test fun highCycloneCapsBandAtMixed() {
        val s = MonthScorer.score(input(cyclone = CycloneRisk.HIGH, exp = Experience("x", ExperienceLevel.PEAK)))
        assertTrue(s.band.ordinal >= Band.MIXED.ordinal, "was ${s.band}")
        assertTrue(s.reasons.any { it.code == ReasonCode.CYCLONE_HIGH })
    }

    @Test fun missingFareRenormalizesAndLowersConfidence() {
        val s = MonthScorer.score(input(fare = null, min = null))
        assertEquals(Confidence.MEDIUM, s.confidence)
        assertTrue(s.reasons.any { it.code == ReasonCode.FARE_MISSING })
        assertEquals(Band.GREAT, s.band) // remaining factors are all good
    }

    @Test fun missingClimateIsLowConfidence() =
        assertEquals(Confidence.LOW, MonthScorer.score(input(climate = null)).confidence)

    @Test fun staleFareIsMediumConfidence() =
        assertEquals(Confidence.MEDIUM, MonthScorer.score(input(age = 8)).confidence)

    @Test fun freshCompleteIsHighConfidence() =
        assertEquals(Confidence.HIGH, MonthScorer.score(input()).confidence)

    @Test fun climateFactorShape() {
        fun c(t: Double, p: Double = 1.0, h: Double = 50.0) =
            MonthScorer.climateScore(ClimateMonth(t, t - 8, p, h))
        assertEquals(1.0, c(18.0), 1e-9)
        assertEquals(1.0, c(28.0), 1e-9)
        assertEquals(0.5 * 0.0 + 0.4 * 1.0 + 0.1 * 1.0, c(8.0), 1e-9)
        assertEquals(0.5 * 0.0 + 0.4 * 1.0 + 0.1 * 1.0, c(36.0), 1e-9)
        assertEquals(0.5 + 0.4 * 0.0 + 0.1, c(24.0, p = 8.0), 1e-9)
        assertTrue(c(30.0, h = 95.0) < c(30.0, h = 50.0)) // humidity penalty when hot
        assertEquals(c(20.0, h = 95.0), c(20.0, h = 50.0), 1e-9) // none when mild
    }

    @Test fun experiencePeakShoulderOther() {
        assertEquals(1.0, MonthScorer.experienceScore(ExperienceLevel.PEAK))
        assertEquals(0.7, MonthScorer.experienceScore(ExperienceLevel.SHOULDER))
        assertEquals(0.3, MonthScorer.experienceScore(ExperienceLevel.NONE))
    }

    @Test fun peakExperienceIsAReasonWithItsId() {
        val s = MonthScorer.score(input(exp = Experience("uyuni-mirror", ExperienceLevel.PEAK)))
        assertTrue(Reason(ReasonCode.EXPERIENCE, "uyuni-mirror") in s.reasons)
    }

    @Test fun atMostTwoReasons() {
        val s = MonthScorer.score(input(exp = Experience("e", ExperienceLevel.PEAK)))
        assertTrue(s.reasons.size <= 2)
    }

    @Test fun hotWetMonthExplainsWhy() {
        val s = MonthScorer.score(input(climate = ClimateMonth(34.0, 26.0, 9.0, 90.0), cyclone = CycloneRisk.LOW))
        assertTrue(s.band.ordinal >= Band.MIXED.ordinal)
        assertTrue(s.reasons.any { it.code == ReasonCode.CLIMATE_WET || it.code == ReasonCode.CLIMATE_HOT })
    }

    @Test fun seismicDataIsNotAnInput() {
        // Structural guarantee: MonthInputs has no seismic field.
        assertTrue(MonthInputs::class.simpleName != null)
        val names = MonthInputs.FIELD_NAMES
        assertTrue(names.none { it.contains("seismic", ignoreCase = true) || it.contains("quake", ignoreCase = true) })
    }
}
