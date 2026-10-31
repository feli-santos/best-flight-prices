package bfp.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import bfp.client.*
import bfp.contracts.*
import bfp.ui.catalog.UiCatalog
import bfp.ui.i18n.Lang
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScreenTest {
    private val catalog = UiCatalog.parse(
        java.io.File("../data/catalog/destinations.json").readText(),
        java.io.File("../data/catalog/countries.json").readText(),
        java.io.File("../data/catalog/airports.json").readText(),
    )
    private fun money(a: String) = MoneyDto(a, "BRL")
    private fun fare(a: String, link: String? = "https://example.test/x") = FareDto(money(a), "2026-10-30T10:00:00Z", FareSource.SAMPLE, link, 1, 1, 600, "LA", "2027-04-10", "2027-04-17")

    private class Repo(
        var explore: suspend () -> ApiResult<ExploreResponse> = { ApiResult.Failure(ApiFailure.Offline) },
        var rec: suspend () -> ApiResult<RecommendationResponse> = { ApiResult.Failure(ApiFailure.Offline) },
        var opts: suspend () -> ApiResult<OptionsResponse> = { ApiResult.Failure(ApiFailure.Offline) },
    ) : TripRepository {
        val explores = mutableListOf<ExploreRequest>(); val recs = mutableListOf<RecommendationRequest>()
        override suspend fun explore(r: ExploreRequest): ApiResult<ExploreResponse> { explores += r; return explore() }
        override suspend fun recommendations(r: RecommendationRequest): ApiResult<RecommendationResponse> { recs += r; return rec() }
        override suspend fun options(r: OptionsRequest) = opts()
    }
    private object NoStore : LastResultStore { override suspend fun load(): String? = null; override suspend fun save(json: String) {} }
    private val platform = object : Platform { override fun openUrl(url: String) {}; override fun nowMs() = 1_793_361_600_000L; override val languageTag = "en" }

    private val exploreResp = ExploreResponse("CNF", listOf(
        ExploreCountryResult("PT", false, listOf(CityResult("pt-lisbon", fare("2500"), FareStatus.OK), CityResult("pt-porto", null, FareStatus.MISSING)), listOf(5, 6, 9)),
        ExploreCountryResult("BR", true, listOf(CityResult("br-sao-paulo", fare("300"), FareStatus.OK)), emptyList()),
    ), "BRL", true, "2026-10-30T12:00:00Z")

    private val recResp = RecommendationResponse("CNF", listOf(PlaceRecommendation("pt-lisbon", StayAdviceDto(7, listOf(5, 7, 9, 11), listOf(ReasonDto(ReasonCodeDto.STAY_LONG_HAUL))),
        listOf(MonthResult("2027-04", BandDto.GREAT, ConfidenceDto.HIGH, fare("2500"), FareStatus.OK, listOf(ReasonDto(ReasonCodeDto.CLIMATE_PLEASANT))),
            MonthResult("2027-05", BandDto.AVOID, ConfidenceDto.LOW, null, FareStatus.MISSING, listOf(ReasonDto(ReasonCodeDto.FARE_MISSING)))),
        fare("2500"), seismicNote = true)), "BRL", false, "2026-10-30T12:00:00Z")

    private val optResp = OptionsResponse("CNF", "pt-lisbon", listOf(OptionDto("o1", 7, fare("2500")), OptionDto("o2", 7, fare("2700", link = null))), "BRL", false, "t")

    private fun ComposeUiTest.launch(repo: Repo, lang: Lang = Lang.EN, scale: Float = 1f, dark: Boolean = false): SearchViewModel {
        val vm = SearchViewModel(repo, NoStore, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), platform::nowMs)
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f, scale)) { App(vm, catalog, platform, lang, dark) } }
        return vm
    }
    private fun ComposeUiTest.type(tag: String, text: String) { onNodeWithTag(tag).performTextInput(text) }

    @Test fun searchButtonIsDisabledUntilAnOriginIsChosen() = runComposeUiTest {
        launch(Repo())
        onNodeWithTag("search").assertIsNotEnabled()
        type("origin", "CNF")
        onNodeWithTag("search").assertIsEnabled()
    }

    @Test fun originSuggestionsAreAccentInsensitiveAndSelectable() = runComposeUiTest {
        launch(Repo())
        type("origin", "belo hor")
        onNodeWithTag("origin-option-CNF").assertExists().performClick()
        onNodeWithTag("search").assertIsEnabled()
    }

    @Test fun noDestinationRunsExploreAndShowsTopCitiesWithBestMonths() = runComposeUiTest {
        val repo = Repo(explore = { ApiResult.Ok(exploreResp) })
        launch(repo)
        type("origin", "CNF"); onNodeWithTag("search").performClick()
        onNodeWithTag("results-title").assertTextEquals("Cheapest places to go")
        onNodeWithTag("country-PT").assertExists()
        onNodeWithTag("city-pt-lisbon").assertExists()
        onNodeWithTag("best-months-PT").assertExists()
        onNodeWithTag("sample-banner").assertExists()
        onNodeWithTag("fare-note").assertExists()
        assertEquals(1, repo.explores.size); assertEquals(0, repo.recs.size)
    }

    @Test fun destinationRunsPlanAndShowsBandsReasonsStayAndSeismicNote() = runComposeUiTest {
        val repo = Repo(rec = { ApiResult.Ok(recResp) })
        launch(repo)
        type("origin", "CNF"); type("destination", "Lisbon"); onNodeWithTag("dest-option-pt-lisbon").performClick()
        onNodeWithTag("stay-7").assertExists()
        onNodeWithTag("search").performClick()
        onNodeWithTag("place-pt-lisbon").assertExists()
        onNodeWithTag("stay-pt-lisbon").assertTextContains("7 nights", substring = true)
        onNodeWithText("Great").assertExists(); onNodeWithText("Avoid").assertExists()
        onNodeWithText("Pleasant weather").assertExists()
        onNodeWithTag("seismic-pt-lisbon").assertExists()
        assertEquals(ScopeDto(ScopeKind.PLACE, "pt-lisbon"), repo.recs.single().scope)
    }

    @Test fun tappingAMonthWithFareOpensOptionsAndNoFareMonthIsNotTappable() = runComposeUiTest {
        launch(Repo(rec = { ApiResult.Ok(recResp) }, opts = { ApiResult.Ok(optResp) }))
        type("origin", "CNF"); type("destination", "Lisbon"); onNodeWithTag("dest-option-pt-lisbon").performClick(); onNodeWithTag("search").performClick()
        onNodeWithTag("month-pt-lisbon-2027-05").performClick()
        onNodeWithTag("options-sheet").assertDoesNotExist()
        onNodeWithTag("month-pt-lisbon-2027-04").performClick()
        onNodeWithTag("options-sheet").assertExists()
        onNodeWithTag("book-o1").assertExists()
        onNodeWithTag("book-o2").assertDoesNotExist() // no provider link, no button
    }

    @Test fun errorsAreShownAndNothingIsInvented() = runComposeUiTest {
        launch(Repo(explore = { ApiResult.Failure(ApiFailure.RateLimited(12)) }))
        type("origin", "CNF"); onNodeWithTag("search").performClick()
        onNodeWithText("12 seconds", substring = true).assertExists()
        onNodeWithTag("country-PT").assertDoesNotExist()
    }

    @Test fun updateRequiredReplacesTheWholeApp() = runComposeUiTest {
        launch(Repo(explore = { ApiResult.Failure(ApiFailure.UpdateRequired) }))
        type("origin", "CNF"); onNodeWithTag("search").performClick()
        onNodeWithTag("update-required").assertExists(); onNodeWithTag("search").assertDoesNotExist()
    }

    @Test fun invalidExactDateBlocksSearch() = runComposeUiTest {
        launch(Repo())
        type("origin", "CNF"); onNodeWithTag("when-exact").performClick(); type("depart", "2027-13-40")
        onNodeWithTag("date-error").assertExists(); onNodeWithTag("search").assertIsNotEnabled()
    }

    @Test fun monthChoiceSendsARangeCoveringTheMonth() = runComposeUiTest {
        val repo = Repo(explore = { ApiResult.Ok(exploreResp) })
        launch(repo)
        type("origin", "CNF"); onNodeWithTag("when-month").performClick(); type("month", "2027-02"); onNodeWithTag("search").performClick()
        assertEquals(DatesDto.Range("2027-02-01", "2027-02-28"), repo.explores.single().dates)
    }

    @Test fun backReturnsToSearch() = runComposeUiTest {
        launch(Repo(explore = { ApiResult.Ok(exploreResp) }))
        type("origin", "CNF"); onNodeWithTag("search").performClick(); onNodeWithTag("back").performClick()
        onNodeWithTag("search").assertExists()
    }

    @Test fun loadingIndicatorWhileWaiting() = runComposeUiTest {
        val gate = CompletableDeferred<ApiResult<ExploreResponse>>()
        launch(Repo(explore = { gate.await() }))
        type("origin", "CNF"); onNodeWithTag("search").performClick()
        onNodeWithTag("loading").assertExists()
        gate.complete(ApiResult.Ok(exploreResp)); waitForIdle()
        onNodeWithTag("loading").assertDoesNotExist()
    }

    /** The whole flow in every language and at 2x text, in light and dark: nothing may crash and key nodes must exist. */
    @Test fun matrixOfLanguagesScalesAndThemes() {
        for (lang in Lang.entries) for (scale in listOf(1f, 2f)) for (dark in listOf(false, true)) {
            runComposeUiTest {
                launch(Repo(explore = { ApiResult.Ok(exploreResp) }, rec = { ApiResult.Ok(recResp) }), lang, scale, dark)
                type("origin", "CNF"); onNodeWithTag("search").performClick()
                onNodeWithTag("country-PT").assertExists()
                onNodeWithTag("city-pt-lisbon").performClick()
                onNodeWithTag("place-pt-lisbon").assertExists()
            }
        }
    }

    @Test fun portugueseAndSpanishTitles() {
        runComposeUiTest { launch(Repo(explore = { ApiResult.Ok(exploreResp) }), Lang.PT); type("origin", "CNF"); onNodeWithTag("search").performClick(); onNodeWithTag("results-title").assertTextEquals("Destinos mais baratos") }
        runComposeUiTest { launch(Repo(explore = { ApiResult.Ok(exploreResp) }), Lang.ES); type("origin", "CNF"); onNodeWithTag("search").performClick(); onNodeWithTag("results-title").assertTextEquals("Destinos más baratos") }
    }

    fun gallery(dir: java.io.File) {
        fun shot(name: String, lang: Lang, dark: Boolean, scale: Float, steps: ComposeUiTest.() -> Unit) = runComposeUiTest {
            launch(Repo(explore = { ApiResult.Ok(exploreResp) }, rec = { ApiResult.Ok(recResp) }, opts = { ApiResult.Ok(optResp) }), lang, scale, dark)
            steps(); waitForIdle()
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(dir, "$name.png"))
        }
        for (lang in Lang.entries) {
            shot("search-${lang.code}", lang, false, 1f) {}
            shot("explore-${lang.code}", lang, false, 1f) { type("origin", "CNF"); onNodeWithTag("search").performClick() }
            shot("plan-${lang.code}", lang, false, 1f) { type("origin", "CNF"); onNodeWithTag("search").performClick(); onNodeWithTag("city-pt-lisbon").performClick() }
        }
        shot("explore-en-dark", Lang.EN, true, 1f) { type("origin", "CNF"); onNodeWithTag("search").performClick() }
        shot("plan-en-2x", Lang.EN, false, 2f) { type("origin", "CNF"); onNodeWithTag("search").performClick(); onNodeWithTag("city-pt-lisbon").performClick() }
    }
}
