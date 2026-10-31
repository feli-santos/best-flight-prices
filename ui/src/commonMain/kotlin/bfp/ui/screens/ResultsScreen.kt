package bfp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bfp.client.ResultState
import bfp.client.SearchAction
import bfp.client.SearchState
import bfp.contracts.*
import bfp.ui.catalog.UiCatalog
import bfp.ui.components.*
import bfp.ui.format.*
import bfp.ui.i18n.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(state: SearchState, catalog: UiCatalog, s: Strings, nowMs: Long, onAction: (SearchAction) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, Modifier.tag("back")) { Text("‹ ${s.back}") }
        val result = state.result
        LazyColumn(Modifier.fillMaxSize().tag("results"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(if (state.mode == bfp.client.SearchMode.EXPLORE) s.exploreTitle else s.planTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.tag("results-title"))
            }
            if (state.loading) item { Loading(s.searching) }
            state.error?.let { e -> item { Notice(s.failure(e), tagId = "error", tone = MaterialTheme.colorScheme.errorContainer) } }
            when (result) {
                is ResultState.Explore -> {
                    if (result.fromCache) item { Notice(s.cachedLabel(result.savedAtMs?.let { ((nowMs - it) / 3_600_000L).toInt().coerceAtLeast(0) }), tagId = "cached") }
                    if (result.response.sample) item { SampleBanner(s) }
                    item { Text(s.fareNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.tag("fare-note")) }
                    if (result.response.countries.isEmpty() && !state.loading) item { Text(s.emptyResults, Modifier.tag("empty")) }
                    items(result.response.countries, key = { it.countryCode }) { c -> CountryCard(c, catalog, s, nowMs, state, onAction) }
                }
                is ResultState.Plan -> {
                    if (result.fromCache) item { Notice(s.cachedLabel(result.savedAtMs?.let { ((nowMs - it) / 3_600_000L).toInt().coerceAtLeast(0) }), tagId = "cached") }
                    if (result.response.sample) item { SampleBanner(s) }
                    item { Text(s.fareNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.tag("fare-note")) }
                    if (result.response.places.isEmpty() && !state.loading) item { Text(s.emptyResults, Modifier.tag("empty")) }
                    items(result.response.places, key = { it.cityId }) { p -> PlaceCard(p, result.response.currency, catalog, s, nowMs, onAction) }
                }
                null -> if (!state.loading && state.error == null) item { Text(s.emptyResults, Modifier.tag("empty")) }
            }
        }
    }
    state.options?.let { o -> OptionsSheet(o.response, catalog, s, nowMs, state.error, onDismiss = { onAction(SearchAction.CloseOptions) }) }
}

@Composable
private fun CountryCard(c: ExploreCountryResult, catalog: UiCatalog, s: Strings, nowMs: Long, state: SearchState, onAction: (SearchAction) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().tag("country-${c.countryCode}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(catalog.countryName(c.countryCode, s.lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (c.domestic) Text(s.domestic, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            c.cities.forEach { city ->
                Row(Modifier.fillMaxWidth().tag("city-${city.cityId}").clickable {
                    onAction(SearchAction.SetDestination(ScopeDto(ScopeKind.PLACE, city.cityId)))
                    onAction(SearchAction.Submit)
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(catalog.cityName(city.cityId, s.lang), style = MaterialTheme.typography.bodyLarge)
                        city.fare?.let { f -> fareLine(f, s, nowMs)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                    FareText(city.fare, city.fareStatus, s)
                }
            }
            if (c.bestMonths.isNotEmpty()) {
                Text("${s.bestMonthsLabel}: ${c.bestMonths.joinToString(", ") { s.months[it - 1] }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.tag("best-months-${c.countryCode}"))
            }
        }
    }
}

@Composable
private fun PlaceCard(p: PlaceRecommendation, currency: String, catalog: UiCatalog, s: Strings, nowMs: Long, onAction: (SearchAction) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().tag("place-${p.cityId}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${catalog.cityName(p.cityId, s.lang)}, ${catalog.countryOfCity(p.cityId)?.let { catalog.countryName(it, s.lang) } ?: ""}".trimEnd(',', ' '), style = MaterialTheme.typography.titleLarge)
            Text("${s.stay}: ${s.nights(p.stay.nights)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.tag("stay-${p.cityId}"))
            p.stay.reasons.forEach { Text(s.reason(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (p.seismicNote) Text(s.seismicNote, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.tag("seismic-${p.cityId}"))
            HorizontalDivider()
            p.months.forEach { m -> MonthRow(p, m, s, nowMs, onAction) }
        }
    }
}

@Composable
private fun MonthRow(p: PlaceRecommendation, m: MonthResult, s: Strings, nowMs: Long, onAction: (SearchAction) -> Unit) {
    Column(Modifier.fillMaxWidth().tag("month-${p.cityId}-${m.yearMonth}").clickable(enabled = m.fare != null) {
        onAction(SearchAction.LoadOptions(p.cityId, m.yearMonth, p.stay.nights))
    }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatMonthYear(m.yearMonth, s.months), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            BandPill(m.band, s)
            FareText(m.fare, m.fareStatus, s)
        }
        m.reasons.forEach { Text(s.reason(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        m.fare?.let { f -> fareLine(f, s, nowMs)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}

@Composable
internal fun FareText(fare: FareDto?, status: FareStatus, s: Strings) {
    when {
        fare != null -> Text(formatMoney(fare.price.amount, fare.price.currency, s.lang.code), style = MaterialTheme.typography.titleMedium, modifier = Modifier.tag("price"))
        status == FareStatus.PENDING -> Text(s.pending, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Text(s.noFare, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Checked 5 h ago" is the only metadata line; empty when the age cannot be read. */
internal fun fareLine(f: FareDto, s: Strings, nowMs: Long): String? = s.fareAgeLabel(fareAge(f.retrievedAt, nowMs)).takeIf { it.isNotEmpty() }
