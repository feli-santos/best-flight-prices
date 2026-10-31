package bfp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bfp.client.ApiFailure
import bfp.client.SearchAction
import bfp.client.SearchViewModel
import bfp.ui.catalog.UiCatalog
import bfp.ui.components.tag
import bfp.ui.i18n.Lang
import bfp.ui.i18n.Strings
import bfp.ui.i18n.stringsFor
import bfp.ui.screens.ResultsScreen
import bfp.ui.screens.SearchScreen
import bfp.ui.theme.BfpTheme

/** Two screens plus a sheet: Search -> Results (-> Options). A hard version gate replaces everything. */
@Composable
fun App(vm: SearchViewModel, catalog: UiCatalog, platform: Platform, langOverride: Lang? = null, dark: Boolean? = null) {
    val state by vm.state.collectAsState()
    val lang = langOverride ?: Lang.fromTag(platform.languageTag)
    val s: Strings = remember(lang) { stringsFor(lang) }
    var showResults by rememberSaveable { mutableStateOf(false) }

    BfpTheme(dark = dark ?: androidx.compose.foundation.isSystemInDarkTheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding()) {
                when {
                    state.error == ApiFailure.UpdateRequired -> UpdateRequired(s)
                    showResults -> ResultsScreen(state, catalog, s, platform.nowMs(), vm::dispatch, onBack = { showResults = false })
                    else -> SearchScreen(state, catalog, s, vm::dispatch, onOpenResults = { showResults = true })
                }
            }
        }
    }
}

@Composable
private fun UpdateRequired(s: Strings) {
    Column(Modifier.padding(24.dp).tag("update-required")) {
        Text(s.updateRequiredTitle, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(s.updateRequiredBody, Modifier.padding(top = 12.dp))
    }
}
