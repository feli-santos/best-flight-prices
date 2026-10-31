package bfp.client

import bfp.contracts.*
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** Everything the UI can show as a failure. Maps 1:1 to localized messages. */
sealed interface ApiFailure {
    data object Offline : ApiFailure
    data object UpdateRequired : ApiFailure
    data class RateLimited(val retryAfterSeconds: Int?) : ApiFailure
    data class ProviderUnavailable(val retryAfterSeconds: Int?) : ApiFailure
    data object ProviderRejected : ApiFailure
    data object CurrencyUnsupported : ApiFailure
    data object InvalidRequest : ApiFailure
    data object Server : ApiFailure
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failure(val failure: ApiFailure) : ApiResult<Nothing>
}

interface TripRepository {
    suspend fun explore(r: ExploreRequest): ApiResult<ExploreResponse>
    suspend fun recommendations(r: RecommendationRequest): ApiResult<RecommendationResponse>
    suspend fun options(r: OptionsRequest): ApiResult<OptionsResponse>
}

class ApiClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val appVersion: String,
) : TripRepository {

    private suspend inline fun <reified Req : Any, reified Res : Any> call(path: String, body: Req): ApiResult<Res> {
        val response: HttpResponse = try {
            http.post("$baseUrl$path") {
                header(ApiPaths.APP_VERSION_HEADER, appVersion)
                contentType(ContentType.Application.Json)
                setBody(ApiJson.encodeToString(serializer<Req>(), body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ApiResult.Failure(ApiFailure.Offline)
        }
        val text = try { response.bodyAsText() } catch (e: CancellationException) { throw e } catch (e: Exception) { return ApiResult.Failure(ApiFailure.Offline) }
        if (response.status.isSuccess()) {
            return try { ApiResult.Ok(ApiJson.decodeFromString(serializer<Res>(), text)) } catch (e: Exception) { ApiResult.Failure(ApiFailure.Server) }
        }
        val problem = try { ApiJson.decodeFromString(Problem.serializer(), text) } catch (e: Exception) { return ApiResult.Failure(ApiFailure.Server) }
        return ApiResult.Failure(mapProblem(problem))
    }

    private fun mapProblem(p: Problem): ApiFailure = when (p.type) {
        ProblemTypes.UPDATE_REQUIRED -> ApiFailure.UpdateRequired
        ProblemTypes.RATE_LIMITED -> ApiFailure.RateLimited(p.retryAfterSeconds)
        ProblemTypes.PROVIDER_UNAVAILABLE, ProblemTypes.BUDGET_EXHAUSTED -> ApiFailure.ProviderUnavailable(p.retryAfterSeconds)
        ProblemTypes.PROVIDER_UNAUTHORIZED -> ApiFailure.ProviderRejected
        ProblemTypes.CURRENCY_UNSUPPORTED -> ApiFailure.CurrencyUnsupported
        ProblemTypes.INVALID_REQUEST, ProblemTypes.UNKNOWN_ORIGIN, ProblemTypes.UNKNOWN_DESTINATION -> ApiFailure.InvalidRequest
        else -> ApiFailure.Server
    }

    override suspend fun explore(r: ExploreRequest): ApiResult<ExploreResponse> = call(ApiPaths.EXPLORE, r)
    override suspend fun recommendations(r: RecommendationRequest): ApiResult<RecommendationResponse> = call(ApiPaths.RECOMMENDATIONS, r)
    override suspend fun options(r: OptionsRequest): ApiResult<OptionsResponse> = call(ApiPaths.OPTIONS, r)
}
