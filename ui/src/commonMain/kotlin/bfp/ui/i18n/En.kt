package bfp.ui.i18n

import bfp.client.ApiFailure
import bfp.contracts.BandDto
import bfp.contracts.ReasonCodeDto
import bfp.contracts.ReasonDto
import bfp.ui.format.FareAge

object EnStrings : Strings {
    override val lang = Lang.EN
    override val appName = "BestFlightPrice"
    override val from = "From"
    override val fromHint = "City or airport code"
    override val to = "To"
    override val toHint = "Anywhere, or a city, country, continent"
    override val popular = "Popular places"
    override val anywhere = "Anywhere"
    override val when_ = "When"
    override val anytime = "Any time"
    override val aMonth = "A month"
    override val exactDates = "Exact dates"
    override val departHint = "Leave (YYYY-MM-DD)"
    override val returnHint = "Return (optional)"
    override val stay = "Stay"
    override val stayAuto = "Suggest for me"
    override val nights = { n: Int -> if (n == 1) "1 night" else "$n nights" }
    override val search = "Find flights"
    override val searching = "Searching fares…"
    override val exploreTitle = "Cheapest places to go"
    override val planTitle = "Best time to go"
    override val back = "Back"
    override val retry = "Try again"
    override val dismiss = "Dismiss"
    override val sampleBadge = "Sample data"
    override val sampleNote = "These are made-up sample prices for testing, not real fares."
    override val noFare = "No fare found"
    override val fareNote = "Lowest fare seen recently for that month. Prices change; check before you buy."
    override val domestic = "Domestic"
    override val bestMonthsLabel = "Best months"
    override val priceFrom = "from"
    override val pending = "Still loading"
    override val options = "Flight options"
    override val noOptions = "No flights found for this month."
    override val book = "See this flight"
    override val direct = "Direct"
    override val stops = { n: Int -> if (n == 1) "1 stop" else "$n stops" }
    override val updateRequiredTitle = "Update required"
    override val updateRequiredBody = "This version is too old to get prices. Install the latest version."
    override val emptyResults = "Nothing to show yet. Pick where you leave from and search."
    override val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    override val cachedLabel = { h: Int? -> if (h == null) "Saved result, you are offline" else "Saved result from $h h ago, you are offline" }
    override val fareAgeLabel = { a: FareAge -> when (a) {
        FareAge.JustNow -> "Checked just now"; is FareAge.Hours -> "Checked ${a.n} h ago"; is FareAge.Days -> "Checked ${a.n} days ago"; FareAge.Unknown -> "" } }
    override val seismicNote = "Earthquakes of magnitude 6+ have happened nearby in the past."
    override val continents = mapOf("Africa" to "Africa", "Asia" to "Asia", "Europe" to "Europe", "North America" to "North America", "Oceania" to "Oceania", "South America" to "South America")
    override val departLabel = "Leave"
    override val returnLabel = "Return"
    override val invalidDate = "Use the format YYYY-MM-DD"

    override fun failure(f: ApiFailure) = when (f) {
        ApiFailure.Offline -> "You are offline. Check your connection and try again."
        ApiFailure.UpdateRequired -> updateRequiredBody
        is ApiFailure.RateLimited -> "Too many searches. Wait ${f.retryAfterSeconds ?: 60} seconds and try again."
        is ApiFailure.ProviderUnavailable -> "Prices are unavailable right now. Try again in a few minutes."
        ApiFailure.ProviderRejected -> "The price service rejected the request. Try again later."
        ApiFailure.CurrencyUnsupported -> "This currency is not supported."
        ApiFailure.InvalidRequest -> "Something in the search is not valid. Check the airport and dates."
        ApiFailure.Server -> "Something went wrong on our side. Try again."
    }
    override fun band(b: BandDto) = when (b) { BandDto.GREAT -> "Great"; BandDto.GOOD -> "Good"; BandDto.MIXED -> "Mixed"; BandDto.AVOID -> "Avoid" }
    override fun experience(id: String) = EN_EXPERIENCES[id] ?: id
    override fun reason(r: ReasonDto) = reasonText(r, ::experience) { c -> when (c) {
        ReasonCodeDto.FARE_CHEAPEST -> "Cheapest fare of the year"
        ReasonCodeDto.FARE_LOW -> "Fare is low"
        ReasonCodeDto.FARE_HIGH -> "Fare is high"
        ReasonCodeDto.FARE_MISSING -> "No fare found for this month"
        ReasonCodeDto.CLIMATE_PLEASANT -> "Pleasant weather"
        ReasonCodeDto.CLIMATE_HOT -> "Very hot"
        ReasonCodeDto.CLIMATE_COLD -> "Very cold"
        ReasonCodeDto.CLIMATE_WET -> "Rainy season"
        ReasonCodeDto.CLIMATE_HUMID -> "Humid"
        ReasonCodeDto.CYCLONE_HIGH -> "High cyclone risk"
        ReasonCodeDto.CYCLONE_MODERATE -> "Some cyclone risk"
        ReasonCodeDto.EXPERIENCE -> "Best time for: {x}"
        ReasonCodeDto.STAY_LONG_HAUL -> "Long trip: stay longer to make it worth it"
        ReasonCodeDto.STAY_TIME_ZONE -> "Big time difference: add a night to recover"
        ReasonCodeDto.STAY_DESTINATION_MIN -> "Most travelers stay at least this long here"
    } }
}

private val EN_EXPERIENCES = mapOf(
    "uyuni-mirror" to "the Uyuni mirror effect", "japan-sakura" to "cherry blossoms", "japan-autumn" to "autumn colors",
    "hokkaido-snow" to "snow and the Sapporo festival", "aurora-north" to "northern lights", "rio-carnival" to "Carnival",
    "europe-christmas" to "Christmas markets", "oktoberfest" to "Oktoberfest", "venice-carnival" to "Venice Carnival",
    "edinburgh-festival" to "Edinburgh festivals", "tulips" to "tulip season", "korea-blossom" to "spring blossoms and autumn colors",
    "canada-foliage" to "autumn colors", "greek-summer" to "beach and island season", "algarve-summer" to "beach season",
    "bali-dry" to "the dry season", "patagonia-summer" to "trekking in Patagonia", "patagonia-ski" to "skiing",
    "cusco-dry" to "Machu Picchu and treks", "maldives-dry" to "the dry season", "phuket-dry" to "the dry season",
    "caribbean-dry" to "the dry season", "masai-mara" to "the Great Migration", "tz-dry" to "safari and treks",
    "cape-summer" to "summer", "nz-ski" to "skiing", "nz-summer" to "summer", "nepal-trek" to "trekking",
    "fiji-dry" to "the dry season", "tahiti-dry" to "the dry season",
)
