package bfp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import bfp.client.SearchAction
import bfp.client.SearchState
import bfp.contracts.DatesDto
import bfp.contracts.ScopeDto
import bfp.contracts.ScopeKind
import bfp.contracts.StayDto
import bfp.ui.catalog.UiCatalog
import bfp.ui.components.Loading
import bfp.ui.components.Notice
import bfp.ui.components.tag
import bfp.ui.format.searchAirports
import bfp.ui.i18n.Strings

private enum class WhenMode { ANY, MONTH, EXACT }

/** Saved by name: iOS cannot auto-save enums. */
private val WhenModeSaver = androidx.compose.runtime.saveable.Saver<WhenMode, String>(save = { it.name }, restore = { WhenMode.valueOf(it) })

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(state: SearchState, catalog: UiCatalog, s: Strings, onAction: (SearchAction) -> Unit, onOpenResults: () -> Unit) {
    var originText by rememberSaveable { mutableStateOf(state.origin) }
    var destText by rememberSaveable { mutableStateOf("") }
    var whenMode by rememberSaveable(stateSaver = WhenModeSaver) { mutableStateOf(WhenMode.ANY) }
    var departText by rememberSaveable { mutableStateOf("") }
    var returnText by rememberSaveable { mutableStateOf("") }
    var monthText by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()

    val airports = remember(originText, catalog) { if (originText.length >= 2 && originText.uppercase() != state.origin) searchAirports(catalog.airports, originText) else emptyList() }
    val places = remember(destText, s.lang) { catalog.searchPlaces(destText, s.lang) }
    val datesError = whenMode == WhenMode.EXACT && ((departText.isNotEmpty() && !isDate(departText)) || (returnText.isNotEmpty() && !isDate(returnText))) ||
        whenMode == WhenMode.MONTH && monthText.isNotEmpty() && !isMonth(monthText)

    fun resolveDates(): DatesDto = when (whenMode) {
        WhenMode.ANY -> DatesDto.Anytime
        WhenMode.MONTH -> if (isMonth(monthText)) DatesDto.Range("$monthText-01", "$monthText-${lastDay(monthText)}") else DatesDto.Anytime
        WhenMode.EXACT -> if (isDate(departText)) DatesDto.Exact(departText, returnText.takeIf { isDate(it) }) else DatesDto.Anytime
    }
    val ready = state.canSearch && !datesError && !state.loading &&
        (whenMode != WhenMode.EXACT || isDate(departText)) && (whenMode != WhenMode.MONTH || isMonth(monthText))

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(s.appName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.tag("title"))

        OutlinedTextField(
            value = originText, onValueChange = { originText = it; onAction(SearchAction.SetOrigin(it.takeIf { v -> v.trim().length == 3 } ?: "")) },
            label = { Text(s.from) }, placeholder = { Text(s.fromHint) }, singleLine = true, modifier = Modifier.fillMaxWidth().tag("origin"),
        )
        airports.forEach { a ->
            Text("${a.city} · ${a.iata} · ${a.name}", Modifier.fillMaxWidth().tag("origin-option-${a.iata}").clickable {
                originText = a.iata; onAction(SearchAction.SetOrigin(a.iata))
            }.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedTextField(
            value = destText, onValueChange = { destText = it; if (it.isEmpty()) onAction(SearchAction.SetDestination(null)) },
            label = { Text(s.to) }, placeholder = { Text(s.toHint) }, singleLine = true, modifier = Modifier.fillMaxWidth().tag("destination"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.destination == null, { destText = ""; onAction(SearchAction.SetDestination(null)) }, { Text(s.anywhere) }, Modifier.tag("dest-anywhere"))
            FilterChip(state.destination?.kind == ScopeKind.POPULAR, { destText = s.popular; onAction(SearchAction.SetDestination(ScopeDto(ScopeKind.POPULAR))) }, { Text(s.popular) }, Modifier.tag("dest-popular"))
            s.continents.forEach { (key, label) ->
                FilterChip(state.destination == ScopeDto(ScopeKind.CONTINENT, key), { destText = label; onAction(SearchAction.SetDestination(ScopeDto(ScopeKind.CONTINENT, key))) }, { Text(label) }, Modifier.tag("dest-continent-$key"))
            }
        }
        places.forEach { c ->
            Text("${c.names[s.lang.code] ?: c.names["en"]}, ${catalog.countryName(c.country, s.lang)}", Modifier.fillMaxWidth().tag("dest-option-${c.id}").clickable {
                destText = c.names[s.lang.code] ?: c.names["en"] ?: c.id; onAction(SearchAction.SetDestination(ScopeDto(ScopeKind.PLACE, c.id)))
            }.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }

        Text(s.when_, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(whenMode == WhenMode.ANY, { whenMode = WhenMode.ANY }, { Text(s.anytime) }, Modifier.tag("when-any"))
            FilterChip(whenMode == WhenMode.MONTH, { whenMode = WhenMode.MONTH }, { Text(s.aMonth) }, Modifier.tag("when-month"))
            FilterChip(whenMode == WhenMode.EXACT, { whenMode = WhenMode.EXACT }, { Text(s.exactDates) }, Modifier.tag("when-exact"))
        }
        if (whenMode == WhenMode.MONTH) {
            OutlinedTextField(monthText, { monthText = it }, label = { Text("YYYY-MM") }, singleLine = true, isError = datesError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().tag("month"))
        }
        if (whenMode == WhenMode.EXACT) {
            OutlinedTextField(departText, { departText = it }, label = { Text(s.departHint) }, singleLine = true, isError = departText.isNotEmpty() && !isDate(departText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().tag("depart"))
            OutlinedTextField(returnText, { returnText = it }, label = { Text(s.returnHint) }, singleLine = true, isError = returnText.isNotEmpty() && !isDate(returnText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().tag("return"))
        }
        if (datesError) Text(s.invalidDate, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.tag("date-error"))

        if (state.destination != null) {
            Text(s.stay, style = MaterialTheme.typography.titleMedium)
            var stayText by rememberSaveable { mutableStateOf("") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(state.stay == StayDto.Auto, { stayText = ""; onAction(SearchAction.SetStay(StayDto.Auto)) }, { Text(s.stayAuto) }, Modifier.tag("stay-auto"))
                listOf(3, 7, 10, 14).forEach { n ->
                    FilterChip(state.stay == StayDto.Nights(n), { stayText = n.toString(); onAction(SearchAction.SetStay(StayDto.Nights(n))) }, { Text(s.nights(n)) }, Modifier.tag("stay-$n"))
                }
            }
        }

        state.error?.let { Notice(s.failure(it), tagId = "error", tone = MaterialTheme.colorScheme.errorContainer) }
        if (state.loading) Loading(s.searching)

        Button(
            onClick = { onAction(SearchAction.SetDates(resolveDates())); onAction(SearchAction.Submit); onOpenResults() },
            enabled = ready, modifier = Modifier.fillMaxWidth().height(52.dp).tag("search"),
        ) { Text(s.search) }
    }
}

internal fun isDate(v: String): Boolean {
    val p = v.split("-")
    if (p.size != 3 || p[0].length != 4 || p[1].length != 2 || p[2].length != 2) return false
    val y = p[0].toIntOrNull() ?: return false; val m = p[1].toIntOrNull() ?: return false; val d = p[2].toIntOrNull() ?: return false
    return m in 1..12 && d in 1..daysIn(y, m)
}
internal fun isMonth(v: String): Boolean {
    val p = v.split("-")
    return p.size == 2 && p[0].length == 4 && p[1].length == 2 && p[0].toIntOrNull() != null && p[1].toIntOrNull() in 1..12
}
private fun daysIn(y: Int, m: Int): Int = when (m) { 2 -> if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
internal fun lastDay(ym: String): String { val p = ym.split("-"); return daysIn(p[0].toInt(), p[1].toInt()).toString().padStart(2, '0') }
