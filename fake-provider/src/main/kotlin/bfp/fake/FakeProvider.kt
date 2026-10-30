package bfp.fake

import bfp.core.FareProvider
import bfp.core.FareQuote
import bfp.core.YearMonth
import bfp.server.data.Catalog
import bfp.server.provider.Money
import bfp.server.provider.SampleFareProvider
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference

val SCENARIOS = setOf(
    "normal", "slow", "timeout", "rate_limited", "unauthorized", "server_error",
    "malformed", "success_false", "empty", "partial", "no_brl",
)

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

private fun quoteJson(q: FareQuote, origin: String, dest: String): String = buildString {
    append("{\"origin\":\"${esc(origin)}\",\"destination\":\"${esc(dest)}\"")
    q.destinationAirport?.let { append(",\"destination_airport\":\"$it\"") }
    append(",\"price\":${Money.format(q.priceMinor)}")
    append(",\"airline\":\"ZZ\",\"flight_number\":\"1\"")
    append(",\"departure_at\":\"${q.departDate}T22:00:00-03:00\"")
    q.returnDate?.let { append(",\"return_at\":\"${it}T10:00:00+00:00\"") }
    append(",\"transfers\":${q.transfers ?: 0},\"return_transfers\":${q.returnTransfers ?: 0}")
    q.durationMinutes?.let { append(",\"duration\":$it") }
    append(",\"link\":\"/search/FAKE${q.departDate}\"}")
}

/**
 * A Travelpayouts-shaped test double. It speaks the documented endpoints and can inject faults,
 * so the real HTTP adapter is exercised in the local lab. All data is synthetic.
 */
fun Application.fakeModule(catalog: Catalog, nowMs: () -> Long = System::currentTimeMillis) {
    val sample = SampleFareProvider(catalog, nowMs)
    val current = AtomicReference("normal")

    suspend fun ApplicationCall.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respondText(body, ContentType.Application.Json, status)

    /** Applies the active fault. Returns true when the request was fully handled. */
    suspend fun ApplicationCall.fault(currency: String): Boolean {
        when (current.get()) {
            "slow" -> delay(3_000)
            "timeout" -> delay(20_000)
            "rate_limited" -> { response.header("X-Rate-Limit-Reset", "5"); json("{}", HttpStatusCode.TooManyRequests); return true }
            "unauthorized" -> { json("{}", HttpStatusCode.Unauthorized); return true }
            "server_error" -> { json("{}", HttpStatusCode.InternalServerError); return true }
            "malformed" -> { json("<html>oops"); return true }
            "success_false" -> { json("{\"success\":false,\"error\":\"fake failure\"}"); return true }
            "empty" -> { json("{\"success\":true,\"data\":{},\"currency\":\"$currency\"}"); return true }
        }
        if (request.header("X-Access-Token").isNullOrBlank()) { json("{}", HttpStatusCode.Unauthorized); return true }
        return false
    }

    fun echoCurrency(requested: String) = if (current.get() == "no_brl") "rub" else requested

    routing {
        get("/__scenario") { call.json("{\"scenario\":\"${current.get()}\",\"available\":[${SCENARIOS.joinToString(",") { "\"$it\"" }}]}") }
        post("/__scenario/{name}") {
            val n = call.parameters["name"].orEmpty()
            if (n !in SCENARIOS) call.json("{\"error\":\"unknown scenario\"}", HttpStatusCode.BadRequest)
            else { current.set(n); call.json("{\"scenario\":\"$n\"}") }
        }
        get("/aviasales/v3/grouped_prices") {
            val p = call.request.queryParameters
            val cur = p["currency"] ?: "rub"
            if (call.fault(cur)) return@get
            val origin = p["origin"] ?: ""; val dest = p["destination"] ?: ""
            val min = p["min_trip_duration"]?.toIntOrNull() ?: 5; val max = p["max_trip_duration"]?.toIntOrNull() ?: 14
            var fares = sample.monthlyCheapest(origin, dest, cur.uppercase(), min..max).toList()
            if (current.get() == "partial") fares = fares.filterIndexed { i, _ -> i % 2 == 0 }
            val data = fares.joinToString(",") { (ym, q) -> "\"${q.departDate}\":${quoteJson(q, origin, dest)}" }
            call.json("{\"success\":true,\"data\":{$data},\"currency\":\"${echoCurrency(cur)}\"}")
        }
        get("/aviasales/v3/prices_for_dates") {
            val p = call.request.queryParameters
            val cur = p["currency"] ?: "rub"
            if (call.fault(cur)) return@get
            val origin = p["origin"] ?: ""; val dest = p["destination"]
            val dep = p["departure_at"]?.let { runCatching { YearMonth.parse(it.take(7)) }.getOrNull() }
            val items: List<FareQuote> = if (dest == null) {
                sample.cheapestByDestination(origin, dep, cur.uppercase())
            } else {
                val ret = p["return_at"]?.let { runCatching { YearMonth.parse(it.take(7)) }.getOrNull() } ?: dep ?: YearMonth(2027, 1)
                sample.roundTrips(origin, dest, dep ?: ret, ret, cur.uppercase())
            }
            val body = items.joinToString(",") { quoteJson(it, origin, it.destination.ifBlank { dest ?: "" }) }
            call.json("{\"success\":true,\"data\":[$body],\"currency\":\"${echoCurrency(cur)}\"}")
        }
    }
}
