package bfp.server.provider

import bfp.core.FareProvider
import bfp.core.FareQuote
import bfp.core.FareSourceKind
import bfp.core.ProviderException
import bfp.core.YearMonth
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Travelpayouts / Aviasales Data API adapter (cached fares from traveler searches of the last ~48h).
 * The token is sent only in the X-Access-Token header. Booking links are built only from provider-supplied paths.
 */
class TravelpayoutsProvider(
    private val http: HttpClient,
    private val baseUrl: String,
    private val token: String,
    private val marker: String?,
    private val nowMs: () -> Long,
) : FareProvider {
    override val name = "travelpayouts"
    override val isSample = false
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun call(path: String, params: Map<String, String>): JsonObject {
        val response = try {
            http.get("$baseUrl$path") {
                header("X-Access-Token", token)
                header("Accept-Encoding", "gzip")
                params.forEach { (k, v) -> parameter(k, v) }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ProviderException(ProviderException.Kind.UNAVAILABLE, "network error: ${e::class.simpleName}")
        }
        val status = response.status
        when {
            status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
                throw ProviderException(ProviderException.Kind.UNAUTHORIZED, "provider rejected credentials")
            status == HttpStatusCode.TooManyRequests ->
                throw ProviderException(ProviderException.Kind.RATE_LIMITED, "provider rate limited", response.headers["X-Rate-Limit-Reset"]?.toIntOrNull())
            !status.isSuccess() -> throw ProviderException(ProviderException.Kind.UNAVAILABLE, "provider status ${status.value}")
        }
        val root = try { json.parseToJsonElement(response.bodyAsText()).jsonObject } catch (_: Exception) {
            throw ProviderException(ProviderException.Kind.BAD_RESPONSE, "malformed provider response")
        }
        val success = root["success"]
        if (success != null && (success !is JsonPrimitive || success.contentOrNull != "true")) {
            throw ProviderException(ProviderException.Kind.BAD_RESPONSE, "provider reported failure")
        }
        return root
    }

    /** Prices must be labelled with the currency the provider actually used; a missing echo with rows is rejected. */
    private fun checkCurrency(root: JsonObject, requested: String) {
        val echoed = (root["currency"] as? JsonPrimitive)?.contentOrNull
        if (echoed == null) {
            if (rows(root["data"]).isNotEmpty()) throw ProviderException(ProviderException.Kind.BAD_RESPONSE, "provider did not state the currency")
            return
        }
        if (!echoed.equals(requested, ignoreCase = true)) {
            throw ProviderException(ProviderException.Kind.CURRENCY_UNSUPPORTED, "provider returned $echoed, requested $requested")
        }
    }

    private fun rows(data: JsonElement?): List<JsonObject> = when (data) {
        is JsonObject -> data.values.mapNotNull { it as? JsonObject }
        is JsonArray -> data.mapNotNull { it as? JsonObject }
        else -> emptyList()
    }

    /** Only provider-supplied Aviasales paths; anything that could change the host is dropped. */
    private fun link(path: String?): String? {
        if (path.isNullOrBlank() || !path.startsWith("/") || path.startsWith("//") || '\\' in path || path.any { it.isISOControl() || it == ' ' }) return null
        val clean = path.substringBefore('#')
        val base = "https://www.aviasales.com$clean"
        if (marker.isNullOrBlank()) return base
        val enc = java.net.URLEncoder.encode(marker, Charsets.UTF_8)
        return base + (if ('?' in base) "&" else "?") + "marker=$enc"
    }

    private fun toQuote(o: JsonObject, currency: String): FareQuote? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        fun i(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
        val price = s("price") ?: return null
        val dep = s("departure_at")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val ret = s("return_at")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val minor = runCatching { Money.toMinor(price) }.getOrNull()?.takeIf { it > 0 } ?: return null
        return FareQuote(
            origin = s("origin") ?: "", destination = s("destination") ?: "", destinationAirport = s("destination_airport"),
            departDate = dep, returnDate = ret, priceMinor = minor, currency = currency.uppercase(),
            transfers = i("transfers"), returnTransfers = i("return_transfers"),
            durationMinutes = i("duration"), airline = s("airline"), deepLink = link(s("link")),
            retrievedAtEpochMs = nowMs(), source = FareSourceKind.TRAVELPAYOUTS,
        )
    }

    override suspend fun monthlyCheapest(origin: String, destination: String, currency: String, tripDays: IntRange): Map<YearMonth, FareQuote> {
        val root = call("/aviasales/v3/grouped_prices", mapOf(
            "origin" to origin, "destination" to destination, "group_by" to "month", "currency" to currency.lowercase(),
            "min_trip_duration" to tripDays.first.toString(), "max_trip_duration" to tripDays.last.toString(),
        ))
        checkCurrency(root, currency)
        val out = mutableMapOf<YearMonth, FareQuote>()
        for (q in rows(root["data"]).mapNotNull { toQuote(it, currency) }) {
            val ym = YearMonth.of(q.departDate)
            val cur = out[ym]
            if (cur == null || q.priceMinor < cur.priceMinor) out[ym] = q
        }
        return out
    }

    override suspend fun roundTrips(origin: String, destination: String, depart: YearMonth, returnMonth: YearMonth, currency: String): List<FareQuote> {
        val root = call("/aviasales/v3/prices_for_dates", mapOf(
            "origin" to origin, "destination" to destination, "departure_at" to depart.toString(), "return_at" to returnMonth.toString(),
            "one_way" to "false", "sorting" to "price", "limit" to "200", "currency" to currency.lowercase(),
        ))
        checkCurrency(root, currency)
        return rows(root["data"]).mapNotNull { toQuote(it, currency) }
    }

    override suspend fun cheapestByDestination(origin: String, depart: YearMonth?, currency: String): List<FareQuote> {
        val params = mutableMapOf("origin" to origin, "one_way" to "false", "unique" to "true", "sorting" to "price", "limit" to "1000", "currency" to currency.lowercase())
        if (depart != null) params["departure_at"] = depart.toString()
        val root = call("/aviasales/v3/prices_for_dates", params)
        checkCurrency(root, currency)
        return rows(root["data"]).mapNotNull { toQuote(it, currency) }
    }
}
