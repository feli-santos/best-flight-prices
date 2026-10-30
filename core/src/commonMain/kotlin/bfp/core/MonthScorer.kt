package bfp.core

data class MonthInputs(
    val month: Int,
    val fareMinor: Long?,
    val minFareMinor: Long?,
    val climate: ClimateMonth?,
    val cyclone: CycloneRisk?,
    val experience: Experience?,
    val fareAgeDays: Int?,
) {
    // Seismic data is intentionally NOT an input: earthquakes never affect ranking.
    companion object {
        val FIELD_NAMES = listOf("month", "fareMinor", "minFareMinor", "climate", "cyclone", "experience", "fareAgeDays")
    }
}

data class Factors(val fare: Double?, val climate: Double?, val experience: Double?, val cyclone: Double?)

data class MonthScore(
    val band: Band,
    val confidence: Confidence,
    val reasons: List<Reason>,
    val factors: Factors,
    val score: Double,
)

object MonthScorer {
    private const val W_FARE = 0.30
    private const val W_CLIMATE = 0.30
    private const val W_EXPERIENCE = 0.25
    private const val W_CYCLONE = 0.15
    private const val STALE_DAYS = 7
    private const val CLIMATE_FLOOR = 0.30
    private const val PEAK_EXPERIENCE_BOOST = 1.0

    private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

    fun fareScore(fare: Long?, min: Long?): Double? =
        if (fare == null || min == null || min <= 0) null else clamp01(1.0 - (fare - min).toDouble() / min)

    fun climateScore(c: ClimateMonth): Double {
        val temp = when {
            c.tMax in 18.0..28.0 -> 1.0
            c.tMax < 18.0 -> clamp01((c.tMax - 8.0) / 10.0)
            else -> clamp01((36.0 - c.tMax) / 8.0)
        }
        val rain = if (c.precipMmDay < 2.0) 1.0 else clamp01(1.0 - (c.precipMmDay - 2.0) / 6.0)
        val humidity = if (c.tMax > 26.0 && c.humidity > 80.0) 1.0 - clamp01((c.humidity - 80.0) / 20.0) else 1.0
        return 0.5 * temp + 0.4 * rain + 0.1 * humidity
    }

    fun experienceScore(level: ExperienceLevel): Double = when (level) {
        ExperienceLevel.PEAK -> 1.0
        ExperienceLevel.SHOULDER -> 0.7
        ExperienceLevel.NONE -> 0.3
    }

    fun cycloneScore(r: CycloneRisk): Double = when (r) {
        CycloneRisk.LOW -> 1.0
        CycloneRisk.MODERATE -> 0.6
        CycloneRisk.HIGH -> 0.1
    }

    fun bandFor(score: Double): Band = when {
        score >= 0.75 -> Band.GREAT
        score >= 0.60 -> Band.GOOD
        score >= 0.45 -> Band.MIXED
        else -> Band.AVOID
    }

    private class Candidate(val reason: Reason, val influence: Double)

    fun score(i: MonthInputs): MonthScore {
        val f = Factors(
            fare = fareScore(i.fareMinor, i.minFareMinor),
            climate = i.climate?.let(::climateScore),
            experience = i.experience?.let { experienceScore(it.level) },
            cyclone = i.cyclone?.let(::cycloneScore),
        )
        val parts = listOf(f.fare to W_FARE, f.climate to W_CLIMATE, f.experience to W_EXPERIENCE, f.cyclone to W_CYCLONE)
            .filter { it.first != null }
        val totalWeight = parts.sumOf { it.second }
        val score = if (totalWeight == 0.0) 0.0 else parts.sumOf { it.first!! * it.second } / totalWeight

        var band = bandFor(score)
        if (i.cyclone == CycloneRisk.HIGH && band.ordinal < Band.MIXED.ordinal) band = Band.MIXED
        if (f.climate != null && f.climate < CLIMATE_FLOOR && band.ordinal < Band.MIXED.ordinal) band = Band.MIXED

        val confidence = when {
            i.climate == null -> Confidence.LOW
            i.fareMinor == null || i.fareAgeDays == null || i.fareAgeDays >= STALE_DAYS -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }

        val cands = mutableListOf<Candidate>()
        fun infl(s: Double, w: Double) = (w / totalWeight) * (s - 0.5)
        if (f.fare != null) {
            val r = when {
                f.fare >= 0.5 && i.fareMinor == i.minFareMinor -> ReasonCode.FARE_CHEAPEST
                f.fare >= 0.5 -> ReasonCode.FARE_LOW
                else -> ReasonCode.FARE_HIGH
            }
            cands += Candidate(Reason(r), infl(f.fare, W_FARE))
        }
        if (f.climate != null && i.climate != null) {
            val s = f.climate
            val code = if (s >= 0.7) ReasonCode.CLIMATE_PLEASANT else when {
                i.climate.precipMmDay > 4.0 -> ReasonCode.CLIMATE_WET
                i.climate.tMax > 28.0 -> if (i.climate.humidity > 80.0) ReasonCode.CLIMATE_HUMID else ReasonCode.CLIMATE_HOT
                else -> ReasonCode.CLIMATE_COLD
            }
            cands += Candidate(Reason(code), infl(s, W_CLIMATE))
        }
        if (f.experience != null && i.experience != null && i.experience.level != ExperienceLevel.NONE) {
            // A peak signature experience is the app's differentiator: always surface it first.
            val boost = if (i.experience.level == ExperienceLevel.PEAK) PEAK_EXPERIENCE_BOOST else 0.0
            cands += Candidate(Reason(ReasonCode.EXPERIENCE, i.experience.id), infl(f.experience, W_EXPERIENCE) + boost)
        }
        if (i.cyclone == CycloneRisk.HIGH) cands += Candidate(Reason(ReasonCode.CYCLONE_HIGH), infl(f.cyclone!!, W_CYCLONE))
        if (i.cyclone == CycloneRisk.MODERATE) cands += Candidate(Reason(ReasonCode.CYCLONE_MODERATE), infl(f.cyclone!!, W_CYCLONE))

        val positives = band == Band.GREAT || band == Band.GOOD
        val ordered = if (positives) cands.sortedByDescending { it.influence } else cands.sortedBy { it.influence }
        val reasons = mutableListOf<Reason>()
        if (i.fareMinor == null) reasons += Reason(ReasonCode.FARE_MISSING)
        for (c in ordered) if (reasons.size < 2) reasons += c.reason
        return MonthScore(band, confidence, reasons, f, score)
    }
}
