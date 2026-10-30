package bfp.fake

import bfp.server.data.Catalog
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("FAKE_PORT")?.toIntOrNull() ?: 8090
    println("fake Travelpayouts provider on :$port (synthetic data; POST /__scenario/{name} to inject faults)")
    embeddedServer(Netty, port = port, host = "0.0.0.0") { fakeModule(Catalog.load()) }.start(wait = true)
}
