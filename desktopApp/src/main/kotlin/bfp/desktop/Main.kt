package bfp.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import bfp.client.*
import bfp.ui.App
import bfp.ui.Platform
import bfp.ui.catalog.UiCatalog
import bfp.ui.i18n.Lang
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File

/** Dev-only harness. Never shipped: it exists to run the shared UI on the Mac with phone sizes and test knobs. */
private class DesktopPlatform : Platform {
    override fun openUrl(url: String) { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    override fun nowMs() = System.currentTimeMillis()
    override val languageTag: String = java.util.Locale.getDefault().toLanguageTag()
}

private class FileStore(private val file: File) : LastResultStore {
    override suspend fun load(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()
    override suspend fun save(json: String) { file.parentFile.mkdirs(); file.writeText(json) }
}

private fun catalog(): UiCatalog {
    val dir = File(System.getProperty("bfp.catalog", "data/catalog"))
    return UiCatalog.parse(File(dir, "destinations.json").readText(), File(dir, "countries.json").readText(), File(dir, "airports.json").readText())
}

fun main() = application {
    val catalog = remember { catalog() }
    val platform = remember { DesktopPlatform() }
    var baseUrl by remember { mutableStateOf(System.getenv("BFP_BASE_URL") ?: "http://localhost:8080") }
    var lang by remember { mutableStateOf<Lang?>(null) }
    var dark by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var generation by remember { mutableStateOf(0) }

    val vm = remember(baseUrl, generation) {
        SearchViewModel(ApiClient(HttpClient(OkHttp), baseUrl, "0.1.0"), FileStore(File(System.getProperty("bfp.local", ".local"), "desktop-last-result.json")), nowMs = platform::nowMs)
    }

    Window(onCloseRequest = ::exitApplication, title = "BestFlightPrice (phone)", state = rememberWindowState(size = DpSize(390.dp, 800.dp))) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) { App(vm, catalog, platform, lang, dark) }
    }
    Window(onCloseRequest = ::exitApplication, title = "Dev panel", state = rememberWindowState(size = DpSize(360.dp, 420.dp))) {
        MaterialTheme {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Backend URL") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Lang?>(null) .plus(Lang.entries).forEach { l -> FilterChip(lang == l, { lang = l }, { Text(l?.code ?: "auto") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f, 1.3f, 2f).forEach { f -> FilterChip(scale == f, { scale = f }, { Text("${f}x") }) }
                    FilterChip(dark, { dark = !dark }, { Text("dark") })
                }
                Button({ generation++ }) { Text("Reset app state") }
            }
        }
    }
}
