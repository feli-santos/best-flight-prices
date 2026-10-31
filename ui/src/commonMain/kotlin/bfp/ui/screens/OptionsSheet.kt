package bfp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import bfp.client.ApiFailure
import bfp.contracts.OptionsResponse
import bfp.ui.catalog.UiCatalog
import bfp.ui.components.Notice
import bfp.ui.components.SampleBanner
import bfp.ui.components.tag
import bfp.ui.format.*
import bfp.ui.i18n.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(r: OptionsResponse, catalog: UiCatalog, s: Strings, nowMs: Long, error: ApiFailure?, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.tag("options-sheet")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${s.options}: ${catalog.cityName(r.cityId, s.lang)}", style = MaterialTheme.typography.titleLarge)
            if (r.sample) SampleBanner(s)
            if (error != null) Notice(s.failure(error), tagId = "error")
            if (r.options.isEmpty()) Text(s.noOptions, Modifier.tag("no-options"))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(r.options, key = { it.id }) { o ->
                    val f = o.fare
                    OutlinedCard(Modifier.fillMaxWidth().tag("option-${o.id}")) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(formatMoney(f.price.amount, f.price.currency, s.lang.code), style = MaterialTheme.typography.titleLarge)
                            val dates = listOfNotNull(f.departDate, f.returnDate).joinToString(" – ") { formatDay(it, s.months, s.lang.code) }
                            if (dates.isNotEmpty()) Text(dates, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(
                                    s.nights(o.nights), f.airline,
                                    f.transfers?.let { if (it == 0) s.direct else s.stops(it) },
                                    f.durationMinutes?.let(::formatDuration),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            fareLine(f, s, nowMs)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            f.deepLink?.let { link -> TextButton(onClick = { uri.openUri(link) }, Modifier.tag("book-${o.id}")) { Text(s.book) } }
                        }
                    }
                }
            }
        }
    }
}
