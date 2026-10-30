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
import kotlinx.coroutines.withTimeoutOrNull
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
    private val deadlineMs: Long = 8_000,
) {
    private companion object Limits {
        const val MAX_PLACES = 30
        const val PER_REQUEST_CONCURRENCY = 4
        const val MAX_EXPLORE_MONTHS = 12
        const val SAME_CITY_KM = 60.0
        val SUPPORTED_CURRENCIES = setOf("USD", "EUR", "GBP", "BRL", "ARS", "CLP", "COP", "MXN", "PEN", "UYU", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "ZAR", "NZD")
    }

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
        if (c !in SUPPORTED_CURRENCIES) throw ApiException(400, ProblemTypes.CURRENCY_UNSUPPORTED, "currency $c is not supported")
    }

    private fun bad(msg: String): Nothing = throw ApiException(400, ProblemTypes.INVALID_REQUEST, msg)

    private fun horizon(): ClosedRange<YearMonth> {
        val first = YearMonth.of(today()).next()
        return first..generateSequence(first) { it.next() }.take(DatePlanner.HORIZON_MONTHS).last()
    }

    /** Parses and validates dates: inside the 12-month horizon, ordered, and trips within the stay limits. */
    private fun intent(d: DatesDto): DateIntent {
        val h = horizon()
        val parsed = try {
            when (d) {
                DatesDto.Anytime -> DateIntent.Flexible
                is DatesDto.Range -> DateIntent.Range(LocalDate.parse(d.from), LocalDate.parse(d.to))
                is DatesDto.Exact -> DateIntent.Exact(LocalDate.parse(d.depart), d.ret?.let(LocalDate::parse))
            }
        } catch (e: IllegalArgumentException) { bad("invalid dates") }
        when (parsed) {
            DateIntent.Flexible -> Unit
            is DateIntent.Range -> if (DatePlanner.months(parsed, today()).isEmpty()) bad("range is outside the next ${DatePlanner.HORIZON_MONTHS} months")
            is DateIntent.Exact -> {
                if (YearMonth.of(parsed.depart) !in h) bad("departure is outside the next ${DatePlanner.HORIZON_MONTHS} months")
                parsed.ret?.let { r ->
                    val n = (r.toEpochDays() - parsed.depart.toEpochDays()).toInt()
                    if (n < StayAdvisor.MIN_NIGHTS || n > StayAdvisor.MAX_NIGHTS) bad("trip must be ${StayAdvisor.MIN_NIGHTS}..${StayAdvisor.MAX_NIGHTS} nights")
                }
            }
        }
        return parsed
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
        val quotes: List<FareQuote> = when (intent) {
            DateIntent.Flexible -> provider.cheapestByDestination(req.origin, null, req.currency)
            is DateIntent.Exact -> provider.cheapestByDestination(req.origin, YearMonth.of(intent.depart), req.currency)
            // `unique` returns one fare per destination overall, so each month is queried and filtered to the range.
            is DateIntent.Range -> coroutineScope {
                DatePlanner.months(intent, today()).take(MAX_EXPLORE_MONTHS)
                    .map { ym -> async { provider.cheapestByDestination(req.origin, ym, req.currency) } }.awaitAll().flatten()
                    .filter { it.departDate >= intent.from && it.departDate <= intent.to }
            }
        }

        val originCity = originCityId(req.origin)
        val countryFares = catalog.topCountries.map { country ->
            val cities = catalog.citiesOf(country.code).filter { it.id != originCity && it.gateway != req.origin && it.airport != req.origin }
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
            ExploreCountryResult(rc.countryCode, rc.domestic, cities, catalog.citiesOf(rc.countryCode).firstOrNull()?.let(::bestMonths).orEmpty())
        }
        return ExploreResponse(req.origin, results, req.currency, provider.isSample, iso(nowMs()))
    }

    /** Catalog city containing the origin airport, so alternate airports of the same city are excluded too. */
    private fun originCityId(origin: String): String? {
        catalog.cityByGateway(origin)?.let { return it.id }
        val ap = catalog.airports[origin] ?: return null
        return catalog.cities.values.firstOrNull { c ->
            c.country == ap.country && (c.airport == origin || distanceKm(c.point, GeoPoint(ap.lat, ap.lon)) <= SAME_CITY_KM)
        }?.id
    }

    /** Top 1..3 calendar months by climate/cyclone/experience only (no provider cost); empty when no data exists. */
    private fun bestMonths(city: CityRow): List<Int> {
        val scored = (1..12).map { it to monthScore(city, it, null, null) }
        if (scored.all { it.second.factors.climate == null && it.second.factors.cyclone == null && it.second.factors.experience == null }) return emptyList()
        return scored.map { it.first to it.second.score }
            .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })
            .take(3).map { it.first }.sorted()
    }

    // ---------- recommendations ----------
    suspend fun recommend(req: RecommendationRequest): RecommendationResponse {
        val (originPt, originTz, _) = checkOrigin(req.origin)
        checkCurrency(req.currency)
        val stayReq = req.stay
        if (stayReq is StayDto.Nights && stayReq.value !in StayAdvisor.MIN_NIGHTS..StayAdvisor.MAX_NIGHTS)
            throw ApiException(400, ProblemTypes.INVALID_REQUEST, "nights must be ${StayAdvisor.MIN_NIGHTS}..${StayAdvisor.MAX_NIGHTS}")
        val dateIntent = intent(req.dates)
        val months = DatePlanner.months(dateIntent, today())
        val targets = resolveScope(req.scope)
        val multi = targets.size > 1
        val permits = Semaphore(PER_REQUEST_CONCURRENCY)
        val fatal = setOf(ProviderException.Kind.UNAUTHORIZED, ProviderException.Kind.RATE_LIMITED, ProviderException.Kind.BUDGET_EXHAUSTED, ProviderException.Kind.CURRENCY_UNSUPPORTED)

        val places = coroutineScope {
            targets.map { city ->
                async {
                    permits.withPermit {
                        try {
                            // Past the deadline, places we could not price are reported as PENDING, never invented.
                            withTimeoutOrNull(deadlineMs) { place(req, city, originPt, originTz, months, dateIntent) }
                                ?: place(req, city, originPt, originTz, months, dateIntent, fares = emptyMap(), status = FareStatus.PENDING)
                        } catch (e: ProviderException) {
                            if (!multi || e.kind in fatal) throw e
                            place(req, city, originPt, originTz, months, dateIntent, fares = emptyMap())
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
        req: RecommendationRequest, city: CityRow, originPt: GeoPoint, originTz: String?, months: List<YearMonth>, intent: DateIntent,
        fares: Map<YearMonth, FareQuote>? = null, status: FareStatus = FareStatus.MISSING,
    ): PlaceRecommendation {
        val firstDate = months.firstOrNull()?.let { LocalDate(it.year, it.month, 15) } ?: today()
        val exactNights = (intent as? DateIntent.Exact)?.ret?.let { (it.toEpochDays() - intent.depart.toEpochDays()).toInt() }
        val requested = req.stay
        val stay = when {
            // The user's own return date defines the trip length and overrides Auto/Nights.
            exactNights != null -> StayAdvice(exactNights, listOf(exactNights), emptyList())
            requested is StayDto.Nights -> StayAdvice(requested.value, listOf(requested.value), emptyList())
            else -> StayAdvisor.adviseForRoute(originPt, city.point, tzShift(originTz, originPt.lon, city.tz, firstDate), city.minNights)
        }
        val tripDays = (stay.nights - 1).coerceAtLeast(1)..(stay.nights + 1)
        val raw = fares ?: provider.monthlyCheapest(req.origin, city.gateway, req.currency, tripDays)
        // A month fare outside the requested range is a real fare that does not match the request: show it as missing.
        val got = if (intent is DateIntent.Range) raw.filterValues { it.departDate >= intent.from && it.departDate <= intent.to } else raw
        val inWindow = months.associateWith { got[it] }
        val minFare = inWindow.values.filterNotNull().minOfOrNull { it.priceMinor }
        val results = months.map { ym ->
            val f = inWindow[ym]
            val s = monthScore(city, ym.month, f, minFare)
            MonthResult(
                ym.toString(), BandDto.valueOf(s.band.name), ConfidenceDto.valueOf(s.confidence.name),
                f?.let(::fareDto), if (f == null) status else FareStatus.OK, s.reasons.map(::reasonDto),
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
        if (ym !in horizon()) bad("yearMonth is outside the next ${DatePlanner.HORIZON_MONTHS} months")
        if (req.nights != null && req.nights !in StayAdvisor.MIN_NIGHTS..StayAdvisor.MAX_NIGHTS) bad("invalid nights")
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

}
