package bfp.server.data

import bfp.core.ClimateMonth
import bfp.core.CycloneRisk
import bfp.core.Experience
import bfp.core.ExperienceLevel
import bfp.core.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable data class Names(val en: String, val pt: String, val es: String)
@Serializable data class AirportRow(val iata: String, val name: String, val city: String, val country: String, val lat: Double, val lon: Double, val size: String, val tz: String? = null)
@Serializable data class CountryRow(val code: String, val continent: String, val rank: Int, val names: Names)
@Serializable data class CityRow(
    val id: String, val country: String, val gateway: String, val airport: String, val lat: Double, val lon: Double,
    val tz: String, val order: Int = 0, val minNights: Int? = null, val names: Names,
) { val point get() = GeoPoint(lat, lon) }

@Serializable private data class ExperienceRow(val id: String, val destinations: List<String>, val peak: List<Int>, val shoulder: List<Int>, val basis: String)
@Serializable private data class CycloneRow(val risk: List<String>, val rate: List<Double>)
@Serializable private data class SeismicRow(val m6Count: Int)

/** Bundled, immutable catalog + server-only data. Loaded once from classpath resources. */
class Catalog private constructor(
    val airports: Map<String, AirportRow>,
    val countries: Map<String, CountryRow>,
    val cities: Map<String, CityRow>,
    private val climate: Map<String, List<ClimateMonth>>,
    private val cyclones: Map<String, List<CycloneRisk>>,
    private val seismic: Map<String, Int>,
    private val experiences: Map<String, Map<Int, Experience>>,
    val version: String,
) {
    private val byGateway: Map<String, List<CityRow>> = cities.values.groupBy { it.gateway }
    val topCountries: List<CountryRow> = countries.values.filter { it.rank > 0 }.sortedBy { it.rank }

    fun cityByGateway(code: String): CityRow? = byGateway[code]?.firstOrNull()
    fun citiesOf(country: String): List<CityRow> = cities.values.filter { it.country == country }.sortedWith(compareBy({ it.order }, { it.id }))
    fun climate(cityId: String, month: Int): ClimateMonth? = climate[cityId]?.getOrNull(month - 1)
    fun cyclone(cityId: String, month: Int): CycloneRisk? = cyclones[cityId]?.getOrNull(month - 1)
    fun hasSeismicNote(cityId: String): Boolean = (seismic[cityId] ?: 0) >= SEISMIC_NOTE_THRESHOLD
    fun experience(cityId: String, month: Int): Experience? = experiences[cityId]?.get(month)

    /** Origin airport or gateway code -> point + tz. Null when unknown. */
    fun originInfo(code: String): Pair<GeoPoint, String?>? {
        airports[code]?.let { return GeoPoint(it.lat, it.lon) to it.tz }
        cityByGateway(code)?.let { return it.point to it.tz }
        return null
    }

    companion object {
        const val SEISMIC_NOTE_THRESHOLD = 20
        private val json = Json { ignoreUnknownKeys = true }

        private fun text(path: String): String =
            Catalog::class.java.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
                ?: error("missing resource $path")

        fun load(): Catalog {
            val airports = json.decodeFromString<List<AirportRow>>(text("/catalog/airports.json")).associateBy { it.iata }
            val countries = json.decodeFromString<List<CountryRow>>(text("/catalog/countries.json")).associateBy { it.code }
            val cities = json.decodeFromString<List<CityRow>>(text("/catalog/destinations.json")).associateBy { it.id }
            val climateRaw = json.parseToJsonElement(text("/server/climate.json")).jsonObject
            val climate = climateRaw.mapValues { (_, v) ->
                v.jsonArray.map { m -> m.jsonArray.map { it.jsonPrimitive.content.toDouble() }.let { ClimateMonth(it[0], it[1], it[2], it[3]) } }
            }
            val cyc = json.decodeFromString<Map<String, CycloneRow>>(text("/server/cyclones.json"))
                .mapValues { (_, r) -> r.risk.map { CycloneRisk.valueOf(it) } }
            val seis = json.decodeFromString<Map<String, SeismicRow>>(text("/server/seismic.json")).mapValues { it.value.m6Count }
            val exps = mutableMapOf<String, MutableMap<Int, Experience>>()
            for (e in json.decodeFromString<List<ExperienceRow>>(text("/server/experiences.json"))) {
                for (d in e.destinations) {
                    val m = exps.getOrPut(d) { mutableMapOf() }
                    for (month in e.shoulder) m.putIfAbsent(month, Experience(e.id, ExperienceLevel.SHOULDER))
                    for (month in e.peak) m[month] = Experience(e.id, ExperienceLevel.PEAK)
                }
            }
            val manifest = json.parseToJsonElement(text("/server/manifest.json")).jsonObject
            val version = manifest["generated"]?.jsonPrimitive?.content ?: "unknown"
            return Catalog(airports, countries, cities, climate, cyc, seis, exps, version)
        }
    }
}
