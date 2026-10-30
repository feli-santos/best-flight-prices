package bfp.server

/** Server configuration; read from environment variables (12-factor). Secrets are never logged. */
data class ServerConfig(
    val provider: String,
    val token: String?,
    val marker: String?,
    val baseUrl: String,
    val ttlSeconds: Int,
    val dailyBudget: Int,
    val ratePerMinute: Int,
    val minAppVersion: String,
    val port: Int = 8080,
    val requestDeadlineMs: Long = 8_000,
    /** Trust X-Forwarded-For (only enable behind a proxy that sets it, e.g. Cloud Run / Fly). */
    val trustProxy: Boolean = false,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): ServerConfig {
            fun int(k: String, d: Int) = env[k]?.toIntOrNull() ?: d
            return ServerConfig(
                provider = env["FARE_PROVIDER"]?.lowercase() ?: "sample",
                token = env["TRAVELPAYOUTS_TOKEN"]?.takeIf { it.isNotBlank() },
                marker = env["TRAVELPAYOUTS_MARKER"]?.takeIf { it.isNotBlank() },
                baseUrl = env["TRAVELPAYOUTS_BASE_URL"] ?: "https://api.travelpayouts.com",
                ttlSeconds = int("CACHE_TTL_SECONDS", 86_400),
                dailyBudget = int("PROVIDER_DAILY_BUDGET", 5_000),
                ratePerMinute = int("RATE_LIMIT_PER_MINUTE", 30),
                minAppVersion = env["MIN_APP_VERSION"] ?: "0.0.0",
                port = int("PORT", 8080),
                requestDeadlineMs = (env["REQUEST_DEADLINE_MS"]?.toLongOrNull() ?: 8_000L),
                trustProxy = env["TRUST_PROXY"]?.lowercase() == "true",
            )
        }
    }

    // Never print the token.
    override fun toString() = "ServerConfig(provider=$provider, baseUrl=$baseUrl, ttl=$ttlSeconds, budget=$dailyBudget, rate=$ratePerMinute, port=$port, deadlineMs=$requestDeadlineMs, trustProxy=$trustProxy)"
}
