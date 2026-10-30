package bfp.server.service

import bfp.contracts.*
import bfp.core.*
import bfp.server.data.Catalog
import bfp.server.data.CityRow
import bfp.server.provider.Money
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Thrown for client errors; mapped to application/problem+json. */
class ApiException(val status: Int, val type: String, override val message: String) : Exception(message)

class RecommendationService(
    private val catalog: Catalog,
    private val provider: FareProvider,
    private val nowMs: () -> Long,
) {
    private val concurrency = Semaphore(4)

    // ---------- helpers ----------
    private fun today(): LocalDate {
        val d = Instant.ofEpochMilli(nowMs()).atZone(ZoneOffset.UTC).toLocalDate()
        return LocalDate(d.year, d.monthValue, d.dayOfMonth)
    }

    private fun checkOrigin(code: String): Triple<GeoPoint, String?, String> {
        if (!Regex("[A-Z]{3}").matches(code)) throw ApiException(400, ProblemTypes.UNKNOWN_ORIGIN, "origin must be a 3-letter IATA code")
        val (pt, tz) = catalog.originInfo(code) ?: throw ApiException(400, ProblemTypes.UNKNOWN_ORIGIN, "unknown origin $code")
        val country = catalog.airports[code]?.country ?: catalog.cityByGateway(code)?.country ?: ""
        return Triple(pt, tz, country)
    }

    private fun checkCurrency(c: String) {
        if (!Regex("[A-Z]{3}").matches(c)) throw ApiException(400, ProblemTypes.INVALID_REQUEST, "currency must be a 3-letter ISO code")
    }

    private fun intent(d: DatesDto): DateIntent = try {
        when (d) {
            DatesDto.Anytime -> DateIntent.Flexible
            is DatesDto.Range -> DateIntent.Range(LocalDate.parse(d.from), LocalDate.parse(d.to))
            is DatesDto.Exact -> DateIntent.Exact(LocalDate.parse(d.depart), d.ret?.let(LocalDate::parse))
        }
    } catch (e: IllegalArgumentException) {
        throw ApiException(400, ProblemTypes.INVALID_REQUEST, "invalid dates")
    }

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    private fun fareDto(q: FareQuote) = FareDto(
        price = MoneyDto(Money.format(q.priceMinor), q.currency), retrievedAt = iso(q.retrievedAtEpochMs),
        source = if (q.source == FareSourceKind.SAMPLE) FareSource.SAMPLE else FareSource.TRAVELPAYOUTS,
        deepLink = q.deepLink, transfers = q.transfers, returnTransfers = q.returnTransfers, durationMinutes = q.durationMinutes,
        airline = q.airline, departDate = q.departDate.toString(), returnDate = q.returnDate?.toString(),
    )

    private fun reasonDto(r: Reason) = ReasonDto(ReasonCodeDto.valueOf(r.code.name), r.arg)

    private fun tzShift(originTz: String?, originLon: Double, destTz: String, at: LocalDate): Double {
        val inst = java.time.LocalDate.of(at.year, at.monthNumber, 15).atStartOfDay(ZoneOffset.UTC).toInstant()
        val dest = ZoneId.of(destTz).rules.getOffset(inst).totalSeconds / 3600.0
        val orig = originTz?.let { ZoneId.of(it).rules.getOffset(inst).totalSeconds / 3600.0 } ?: (originLon / 15.0)
        return dest - orig
    }

    private fun matches(q: FareQuote, c: CityRow) = q.destination == c.gateway || q.destinationAirport == c.airport || q.destination == c.airport

    private fun staleDays(q: FareQuote): Int = ((nowMs() - q.retrievedAtEpochMs) / 86_400_000L).toInt()

    private fun monthScore(city: CityRow, month: Int, fare: FareQuote?, min: Long?) = MonthScorer.score(
        MonthInputs(month, fare?.priceMinor, min, catalog.climate(city.id, month), catalog.cyclone(city.id, month), catalog.experience(city.id, month), fare?.let(::staleDays)),
    )

    // ---------- explore ----------
    suspend fun explore(req: ExploreRequest): ExploreResponse {
        val (_, _, originCountry) = checkOrigin(req.origin)
        checkCurrency(req.currency)
        val intent = intent(req.dates)
        val departMonth = (intent as? DateIntent.Exact)?.let { YearMonth.of(it.depart) }
        val quotes = provider.cheapestByDestination(req.origin, departMonth, req.currency)
            .filter { q -> (intent as? DateIntent.Range)?.let { q.departDate >= it.from && q.departDate <= it.to } ?: true }

        val countryFares = catalog.topCountries.map { country ->
            val cities = catalog.citiesOf(country.code).filter { it.gateway != req.origin && it.airport != req.origin }
            CountryFares(country.code, cities.map { c ->
                CityFare(c.id, quotes.filter { matches(it, c) }.minOfOrNull { it.priceMinor })
            })
        }
        val ranked = ExploreRanker.rank("", originCountry, countryFares)
        val results = ranked.map { rc ->
            val cities = rc.cities.map { cf ->
                val q = quotes.filter { matches(it, catalog.cities.getValue(cf.cityId)) }.minByOrNull { it.priceMinor }
                CityResult(cf.cityId, q?.let(::fareDto), if (q == null) FareStatus.MISSING else FareStatus.OK)
            }
            ExploreCountryResult(rc.countryCode, rc.domestic, cities, bestMonths(catalog.cities.getValue(rc.cities.first().cityId)))
        }
        return ExploreResponse(req.origin, results, req.currency, provider.isSample, iso(nowMs()))
    }

    /** Top 1..3 calendar months by climate/cyclone/experience only (no provider cost). */
    private fun bestMonths(city: CityRow): List<Int> =
        (1..12).map { it to monthScore(city, it, null, null).score }
            .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })
            .take(3).map { it.first }.sorted()

    // ---------- recommendations ----------
    suspend fun recommend(req: RecommendationRequest): RecommendationResponse {
        val (originPt, originTz, _) = checkOrigin(req.origin)
        checkCurrency(req.currency)
        val stayReq = req.stay
        if (stayReq is StayDto.Nights && stayReq.value !in StayAdvisor.MIN_NIGHTS..StayAdvisor.MAX_NIGHTS)
            throw ApiException(400, ProblemTypes.INVALID_REQUEST, "nights must be ${StayAdvisor.MIN_NIGHTS}..${StayAdvisor.MAX_NIGHTS}")
        val months = DatePlanner.months(intent(req.dates), today())
        val targets = resolveScope(req.scope)
        val multi = targets.size > 1

        val places = coroutineScope {
            targets.map { city ->
                async {
                    concurrency.withPermit {
                        try { place(req, city, originPt, originTz, months) }
                        catch (e: ProviderException) {
                            if (!multi || e.kind in setOf(ProviderException.Kind.UNAUTHORIZED, ProviderException.Kind.RATE_LIMITED, ProviderException.Kind.BUDGET_EXHAUSTED, ProviderException.Kind.CURRENCY_UNSUPPORTED)) throw e
                            place(req, city, originPt, originTz, months, fares = emptyMap())
                        }
                    }
                }
            }.awaitAll()
        }
        val ordered = if (!multi) places else places.sortedWith(
            compareBy<PlaceRecommendation> { it.cheapest == null }.thenBy { it.cheapest?.price?.amount?.toBigDecimal() }.thenBy { it.cityId },
        )
        return RecommendationResponse(req.origin, ordered, req.currency, provider.isSample, iso(nowMs()))
    }

    private fun resolveScope(scope: ScopeDto): List<CityRow> {
        val cities = when (scope.kind) {
            ScopeKind.PLACE -> listOfNotNull(scope.id?.let { catalog.cities[it] })
            ScopeKind.COUNTRY -> scope.id?.let { catalog.citiesOf(it.uppercase()) }.orEmpty()
            ScopeKind.CONTINENT -> catalog.topCountries.filter { it.continent.equals(scope.id, true) }.flatMap { catalog.citiesOf(it.code).take(1) }
            ScopeKind.POPULAR -> catalog.topCountries.take(20).flatMap { catalog.citiesOf(it.code).take(1) }
        }
        if (cities.isEmpty()) throw ApiException(400, ProblemTypes.UNKNOWN_DESTINATION, "unknown destination")
        return cities.take(MAX_PLACES)
    }

    private suspend fun place(
        req: RecommendationRequest, city: CityRow, originPt: GeoPoint, originTz: String?, months: List<YearMonth>,
        fares: Map<YearMonth, FareQuote>? = null,
    ): PlaceRecommendation {
        val firstDate = months.firstOrNull()?.let { LocalDate(it.year, it.month, 15) } ?: today()
        val stay = when (val s = req.stay) {
            is StayDto.Nights -> StayAdvice(s.value, listOf(s.value), emptyList())
            StayDto.Auto -> StayAdvisor.adviseForRoute(originPt, city.point, tzShift(originTz, originPt.lon, city.tz, firstDate), city.minNights)
        }
        val tripDays = (stay.nights - 1).coerceAtLeast(1)..(stay.nights + 1)
        val got = fares ?: provider.monthlyCheapest(req.origin, city.gateway, req.currency, tripDays)
        val inWindow = months.associateWith { got[it] }
        val minFare = inWindow.values.filterNotNull().minOfOrNull { it.priceMinor }
        val results = months.map { ym ->
            val f = inWindow[ym]
            val s = monthScore(city, ym.month, f, minFare)
            MonthResult(
                ym.toString(), BandDto.valueOf(s.band.name), ConfidenceDto.valueOf(s.confidence.name),
                f?.let(::fareDto), if (f == null) FareStatus.MISSING else FareStatus.OK, s.reasons.map(::reasonDto),
            )
        }
        val cheapest = inWindow.values.filterNotNull().minByOrNull { it.priceMinor }
        return PlaceRecommendation(
            city.id, StayAdviceDto(stay.nights, stay.candidates, stay.reasons.map(::reasonDto)), results,
            cheapest?.let(::fareDto), catalog.hasSeismicNote(city.id),
        )
    }

    // ---------- options ----------
    suspend fun options(req: OptionsRequest): OptionsResponse {
        checkOrigin(req.origin)
        checkCurrency(req.currency)
        val city = catalog.cities[req.cityId] ?: throw ApiException(400, ProblemTypes.UNKNOWN_DESTINATION, "unknown destination")
        val ym = try { YearMonth.parse(req.yearMonth) } catch (e: Exception) { throw ApiException(400, ProblemTypes.INVALID_REQUEST, "yearMonth must be YYYY-MM") }
        if (req.nights != null && req.nights !in StayAdvisor.MIN_NIGHTS..StayAdvisor.MAX_NIGHTS) throw ApiException(400, ProblemTypes.INVALID_REQUEST, "invalid nights")
        val quotes = listOf(ym, ym.next()).distinct().flatMap { provider.roundTrips(req.origin, city.gateway, ym, it, req.currency) }
            .filter { it.returnDate != null && YearMonth.of(it.departDate) == ym && DatePlanner.matchesStay(it.departDate, it.returnDate!!, req.nights) }
            .distinctBy { Triple(it.departDate, it.returnDate, it.priceMinor) }
        val byId = quotes.mapIndexed { i, q -> "o$i" to q }.toMap()
        val ranked = ItineraryRanker.rank(byId.map { (id, q) ->
            Itinerary(id, q.priceMinor, q.durationMinutes, (q.transfers ?: 0) + (q.returnTransfers ?: 0), q.departDate)
        })
        val opts = ranked.map { r ->
            val q = byId.getValue(r.id)
            OptionDto(r.id, (q.returnDate!!.toEpochDays() - q.departDate.toEpochDays()).toInt(), fareDto(q))
        }
        return OptionsResponse(req.origin, req.cityId, opts, req.currency, provider.isSample, iso(nowMs()))
    }

    companion object { const val MAX_PLACES = 30 }
}
