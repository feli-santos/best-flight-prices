package bfp.ui

import bfp.client.ApiFailure
import bfp.contracts.*
import bfp.ui.format.FareAge
import bfp.ui.i18n.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringsTest {
    private val all = Lang.entries.map { stringsFor(it) }

    private val failures = listOf(
        ApiFailure.Offline, ApiFailure.UpdateRequired, ApiFailure.RateLimited(5), ApiFailure.RateLimited(null),
        ApiFailure.ProviderUnavailable(null), ApiFailure.ProviderRejected, ApiFailure.CurrencyUnsupported, ApiFailure.InvalidRequest, ApiFailure.Server,
    )

    @Test fun everyFailureHasATextInEveryLanguage() = all.forEach { s -> failures.forEach { assertTrue(s.failure(it).isNotBlank(), "${s.lang} $it") } }

    @Test fun everyReasonCodeHasATextAndNoRawPlaceholders() = all.forEach { s ->
        ReasonCodeDto.entries.forEach { code ->
            val t = s.reason(ReasonDto(code, if (code == ReasonCodeDto.EXPERIENCE) "japan-sakura" else null))
            assertTrue(t.isNotBlank(), "${s.lang} $code"); assertFalse("{x}" in t, "${s.lang} $code")
        }
    }

    @Test fun bandsAreDistinctWords() = all.forEach { s -> assertEquals(4, BandDto.entries.map { s.band(it) }.toSet().size, s.lang.name) }

    @Test fun everyExperienceIdIsTranslatedInEveryLanguage() {
        val ids = Regex("\"id\":\\s*\"([a-z0-9-]+)\"").findAll(java.io.File("../data/server/experiences.json").readText()).map { it.groupValues[1] }.toList()
        assertEquals(30, ids.size)
        all.forEach { s -> ids.forEach { assertFalse(s.experience(it) == it, "${s.lang} missing experience $it") } }
    }

    @Test fun twelveMonthsAndAllContinents() = all.forEach { s ->
        assertEquals(12, s.months.size)
        assertEquals(setOf("Africa", "Asia", "Europe", "North America", "Oceania", "South America"), s.continents.keys)
    }

    @Test fun pluralsAndAges() = all.forEach { s ->
        assertTrue(s.nights(1) != s.nights(2)); assertTrue(s.stops(1) != s.stops(2))
        assertTrue(s.fareAgeLabel(FareAge.Hours(5)).contains("5")); assertEquals("", s.fareAgeLabel(FareAge.Unknown))
    }

    @Test fun langFromTag() {
        assertEquals(Lang.PT, Lang.fromTag("pt-BR")); assertEquals(Lang.ES, Lang.fromTag("es_MX")); assertEquals(Lang.EN, Lang.fromTag("fr-FR"))
    }
}
