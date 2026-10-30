package bfp.server

import bfp.contracts.*
import bfp.core.FareProvider
import bfp.core.ProviderException
import bfp.server.data.Catalog
import bfp.server.service.ApiException
import bfp.server.service.RecommendationService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlin.time.Duration.Companion.seconds

private val PROBLEM_JSON = ContentType("application", "problem+json")
private val API_LIMIT = RateLimitName("api")

internal suspend fun ApplicationCall.problem(status: HttpStatusCode, type: String, title: String, detail: String? = null, retryAfter: Int? = null) {
    respondText(ApiJson.encodeToString(Problem(type, title, status.value, detail, retryAfter)), PROBLEM_JSON, status)
}

/** "1.2.3" -> comparable list; non-numeric parts count as 0. */
internal fun versionParts(v: String): List<Int> = v.split(".", "-", "+").take(3).map { it.toIntOrNull() ?: 0 }.let { it + List(3 - it.size) { 0 } }
internal fun versionLessThan(a: String, b: String): Boolean {
    val x = versionParts(a); val y = versionParts(b)
    for (i in 0 until 3) if (x[i] != y[i]) return x[i] < y[i]
    return false
}

private fun versionGate(min: String) = createApplicationPlugin("VersionGate") {
    onCall { call ->
        val path = call.request.path()
        if (path == ApiPaths.HEALTH) return@onCall
        val v = call.request.header(ApiPaths.APP_VERSION_HEADER) ?: return@onCall
        if (versionLessThan(v, min)) {
            call.problem(HttpStatusCode.UpgradeRequired, ProblemTypes.UPDATE_REQUIRED, "Update required", "minimum app version is $min")
        }
    }
}

fun Application.module(cfg: ServerConfig, catalog: Catalog, provider: FareProvider, nowMs: () -> Long = System::currentTimeMillis) {
    val service = RecommendationService(catalog, provider, nowMs)

    install(ContentNegotiation) { json(ApiJson) }
    install(CallLogging) { filter { it.request.path() != ApiPaths.HEALTH } }
    install(RateLimit) {
        register(API_LIMIT) {
            rateLimiter(limit = cfg.ratePerMinute, refillPeriod = 60.seconds)
            requestKey { it.request.origin.remoteHost }
        }
    }
    install(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.problem(HttpStatusCode.TooManyRequests, ProblemTypes.RATE_LIMITED, "Too many requests", retryAfter = 30)
        }
        exception<ApiException> { call, e -> call.problem(HttpStatusCode.fromValue(e.status), e.type, e.message) }
        exception<SerializationException> { call, _ -> call.problem(HttpStatusCode.BadRequest, ProblemTypes.INVALID_REQUEST, "Invalid request body") }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ -> call.problem(HttpStatusCode.BadRequest, ProblemTypes.INVALID_REQUEST, "Invalid request") }
        exception<ProviderException> { call, e ->
            when (e.kind) {
                ProviderException.Kind.UNAUTHORIZED -> call.problem(HttpStatusCode.BadGateway, ProblemTypes.PROVIDER_UNAUTHORIZED, "Fare provider rejected our credentials")
                ProviderException.Kind.CURRENCY_UNSUPPORTED -> call.problem(HttpStatusCode.BadRequest, ProblemTypes.CURRENCY_UNSUPPORTED, "Currency not supported by the fare provider")
                ProviderException.Kind.BUDGET_EXHAUSTED -> call.problem(HttpStatusCode.ServiceUnavailable, ProblemTypes.BUDGET_EXHAUSTED, "Daily fare budget reached", retryAfter = 3600)
                else -> call.problem(HttpStatusCode.ServiceUnavailable, ProblemTypes.PROVIDER_UNAVAILABLE, "Fare provider unavailable", retryAfter = e.retryAfterSeconds ?: 30)
            }
        }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("unhandled error: ${e::class.simpleName}")
            call.problem(HttpStatusCode.InternalServerError, "internal", "Internal error")
        }
    }
    install(versionGate(cfg.minAppVersion))

    routing {
        get(ApiPaths.HEALTH) {
            call.respond(HealthResponse("ok", API_VERSION, catalog.version, provider.name, provider.isSample))
        }
        rateLimit(API_LIMIT) {
            post(ApiPaths.EXPLORE) { call.respond(service.explore(call.receive<ExploreRequest>())) }
            post(ApiPaths.RECOMMENDATIONS) { call.respond(service.recommend(call.receive<RecommendationRequest>())) }
            post(ApiPaths.OPTIONS) { call.respond(service.options(call.receive<OptionsRequest>())) }
        }
    }
}
