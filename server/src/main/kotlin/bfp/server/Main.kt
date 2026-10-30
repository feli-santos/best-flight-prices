package bfp.server

import bfp.core.FareProvider
import bfp.server.data.Catalog
import bfp.server.provider.GuardedProvider
import bfp.server.provider.ProviderBudget
import bfp.server.provider.SampleFareProvider
import bfp.server.provider.TravelpayoutsProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun buildProvider(cfg: ServerConfig, catalog: Catalog, nowMs: () -> Long = System::currentTimeMillis): FareProvider {
    val inner: FareProvider = when (cfg.provider) {
        "sample" -> SampleFareProvider(catalog, nowMs)
        "travelpayouts" -> {
            val token = cfg.token ?: error("FARE_PROVIDER=travelpayouts requires TRAVELPAYOUTS_TOKEN")
            val http = HttpClient(CIO) {
                followRedirects = false // never forward the access token to another host
                install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 5_000 }
                install(ContentEncoding) { gzip() }
            }
            TravelpayoutsProvider(http, cfg.baseUrl, token, cfg.marker, nowMs)
        }
        else -> error("unknown FARE_PROVIDER '${cfg.provider}' (sample | travelpayouts)")
    }
    val day = { nowMs() / 86_400_000L }
    return GuardedProvider(inner, cfg.ttlSeconds * 1000L, ProviderBudget(cfg.dailyBudget, day), nowMs)
}

fun main() {
    val cfg = ServerConfig.fromEnv()
    val catalog = Catalog.load()
    val provider = buildProvider(cfg, catalog)
    println("starting $cfg")
    embeddedServer(Netty, port = cfg.port, host = "0.0.0.0") { module(cfg, catalog, provider) }.start(wait = true)
}
