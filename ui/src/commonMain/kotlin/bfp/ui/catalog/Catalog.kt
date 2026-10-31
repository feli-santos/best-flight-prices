package bfp.ui.catalog

import bfp.ui.format.AirportEntry
import bfp.ui.i18n.Lang
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class CityRow(val id: String, val country: String, val airport: String, val gateway: String, val names: Map<String, String>, val order: Int = 0)
@Serializable data class CountryRow(val code: String, val continent: String, val names: Map<String, String>, val rank: Int? = null)
@Serializable private data class AirportRow(val iata: String, val name: String, val city: String, val country: String, val size: String)

/** Names for display and the airport list for the "From" field. Same files on every platform. */
class UiCatalog(val cities: List<CityRow>, val countries: List<CountryRow>, val airports: List<AirportEntry>) {
    private val cityById = cities.associateBy { it.id }
    private val countryByCode = countries.associateBy { it.code }
    fun cityName(id: String, lang: Lang): String = cityById[id]?.names?.get(lang.code) ?: cityById[id]?.names?.get("en") ?: id
    fun countryName(code: String, lang: Lang): String = countryByCode[code]?.names?.get(lang.code) ?: countryByCode[code]?.names?.get("en") ?: code
    fun countryOfCity(id: String): String? = cityById[id]?.country

    /** Destination picker search over city and country names (accent- and case-insensitive). */
    fun searchPlaces(query: String, lang: Lang, limit: Int = 6): List<CityRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return cities.filter { c ->
            (c.names[lang.code] ?: c.names["en"] ?: "").lowercase().contains(q) ||
                countryName(c.country, lang).lowercase().contains(q)
        }.sortedBy { it.order }.take(limit)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(cities: String, countries: String, airports: String): UiCatalog = UiCatalog(
            json.decodeFromString(cities),
            json.decodeFromString(countries),
            json.decodeFromString<List<AirportRow>>(airports).map { AirportEntry(it.iata, it.name, it.city, it.country, it.size) },
        )
        val EMPTY = UiCatalog(emptyList(), emptyList(), emptyList())
    }
}
