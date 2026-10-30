package bfp.core

data class CityFare(val cityId: String, val priceMinor: Long?)
data class CountryFares(val countryCode: String, val cities: List<CityFare>)
data class ExploreCountry(
    val countryCode: String,
    val cities: List<CityFare>,
    val cheapest: Long?,
    val domestic: Boolean,
)

object ExploreRanker {
    const val MAX_CITIES = 3

    fun rank(originCityId: String, originCountry: String, countries: List<CountryFares>): List<ExploreCountry> =
        countries.mapNotNull { c ->
            val cities = c.cities.filter { it.cityId != originCityId }
                .sortedWith(compareBy<CityFare> { it.priceMinor == null }.thenBy { it.priceMinor ?: 0L }.thenBy { it.cityId })
                .take(MAX_CITIES)
            if (cities.isEmpty()) null
            else ExploreCountry(c.countryCode, cities, cities.first().priceMinor, c.countryCode == originCountry)
        }.sortedWith(compareBy<ExploreCountry> { it.cheapest == null }.thenBy { it.cheapest ?: 0L }.thenBy { it.countryCode })
}
