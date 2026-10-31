package bfp.client

import bfp.contracts.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val problemHeaders = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, body: String, headers: io.ktor.http.Headers = json, seen: MutableList<io.ktor.client.request.HttpRequestData> = mutableListOf()) =
        ApiClient(HttpClient(MockEngine { req -> seen += req; respond(body, status, headers) }), "http://x", "1.2.3")

    private val explore = ExploreResponse("CNF", emptyList(), "BRL", true, "2026-10-30T00:00:00Z")

    @Test fun successDecodesAndSendsVersionHeader() = runTest {
        val seen = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val r = client(body = ApiJson.encodeToString(explore), seen = seen).explore(ExploreRequest("CNF"))
        assertIs<ApiResult.Ok<ExploreResponse>>(r)
        assertEquals("1.2.3", seen.single().headers[ApiPaths.APP_VERSION_HEADER])
        assertEquals("http://x/v1/explore", seen.single().url.toString())
    }

    private suspend fun failure(status: HttpStatusCode, type: String, retry: Int? = null): ApiFailure {
        val body = ApiJson.encodeToString(Problem(type, "t", status.value, retryAfterSeconds = retry))
        val r = client(status, body, problemHeaders).explore(ExploreRequest("CNF"))
        return assertIs<ApiResult.Failure>(r).failure
    }

    @Test fun mapsProblemTypes() = runTest {
        assertEquals(ApiFailure.UpdateRequired, failure(HttpStatusCode.UpgradeRequired, ProblemTypes.UPDATE_REQUIRED))
        assertEquals(ApiFailure.RateLimited(12), failure(HttpStatusCode.TooManyRequests, ProblemTypes.RATE_LIMITED, 12))
        assertEquals(ApiFailure.ProviderUnavailable(30), failure(HttpStatusCode.ServiceUnavailable, ProblemTypes.PROVIDER_UNAVAILABLE, 30))
        assertEquals(ApiFailure.ProviderUnavailable(3600), failure(HttpStatusCode.ServiceUnavailable, ProblemTypes.BUDGET_EXHAUSTED, 3600))
        assertEquals(ApiFailure.ProviderRejected, failure(HttpStatusCode.BadGateway, ProblemTypes.PROVIDER_UNAUTHORIZED))
        assertEquals(ApiFailure.CurrencyUnsupported, failure(HttpStatusCode.BadRequest, ProblemTypes.CURRENCY_UNSUPPORTED))
        assertEquals(ApiFailure.InvalidRequest, failure(HttpStatusCode.BadRequest, ProblemTypes.UNKNOWN_ORIGIN))
    }

    @Test fun unparseableErrorBodyIsServerError() = runTest {
        val r = client(HttpStatusCode.InternalServerError, "<html>", json).explore(ExploreRequest("CNF"))
        assertEquals(ApiFailure.Server, assertIs<ApiResult.Failure>(r).failure)
    }

    @Test fun malformedSuccessBodyIsServerError() = runTest {
        val r = client(body = "{nope").explore(ExploreRequest("CNF"))
        assertEquals(ApiFailure.Server, assertIs<ApiResult.Failure>(r).failure)
    }

    @Test fun networkErrorIsOffline() = runTest {
        val c = ApiClient(HttpClient(MockEngine { throw java.io.IOException("down") }), "http://x", "1")
        assertEquals(ApiFailure.Offline, assertIs<ApiResult.Failure>(c.explore(ExploreRequest("CNF"))).failure)
    }

    @Test fun cancellationIsNotSwallowed() = runTest {
        val c = ApiClient(HttpClient(MockEngine { throw kotlinx.coroutines.CancellationException("c") }), "http://x", "1")
        val r = runCatching { c.explore(ExploreRequest("CNF")) }
        assertTrue(r.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
}
