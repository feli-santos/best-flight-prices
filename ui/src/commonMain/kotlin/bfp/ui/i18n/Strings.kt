package bfp.ui.i18n

import bfp.client.ApiFailure
import bfp.contracts.BandDto
import bfp.contracts.ReasonCodeDto
import bfp.contracts.ReasonDto
import bfp.ui.format.FareAge

enum class Lang(val code: String) { EN("en"), PT("pt"), ES("es");
    companion object { fun fromTag(tag: String): Lang = entries.firstOrNull { tag.lowercase().startsWith(it.code) } ?: EN }
}

/**
 * Every user-visible string. One implementation per language; a missing translation is a compile error,
 * which is the strings-parity guarantee for both platforms.
 */
interface Strings {
    val lang: Lang
    val appName: String
    val from: String
    val fromHint: String
    val to: String
    val toHint: String
    val popular: String
    val anywhere: String
    val when_: String
    val anytime: String
    val aMonth: String
    val exactDates: String
    val departHint: String
    val returnHint: String
    val stay: String
    val stayAuto: String
    val nights: (Int) -> String
    val search: String
    val searching: String
    val exploreTitle: String
    val planTitle: String
    val back: String
    val retry: String
    val dismiss: String
    val sampleBadge: String
    val sampleNote: String
    val noFare: String
    val fareNote: String
    val domestic: String
    val bestMonthsLabel: String
    val priceFrom: String
    val pending: String
    val options: String
    val noOptions: String
    val book: String
    val direct: String
    val stops: (Int) -> String
    val updateRequiredTitle: String
    val updateRequiredBody: String
    val emptyResults: String
    val months: List<String>
    val cachedLabel: (Int?) -> String
    val fareAgeLabel: (FareAge) -> String
    val seismicNote: String
    val continents: Map<String, String>
    fun failure(f: ApiFailure): String
    fun band(b: BandDto): String
    fun reason(r: ReasonDto): String
    fun experience(id: String): String
    val departLabel: String
    val returnLabel: String
    val invalidDate: String
}

fun stringsFor(lang: Lang): Strings = when (lang) { Lang.EN -> EnStrings; Lang.PT -> PtStrings; Lang.ES -> EsStrings }

/** Shared helper so the reason list is matched exhaustively in each language. */
internal inline fun reasonText(r: ReasonDto, exp: (String) -> String, text: (ReasonCodeDto) -> String): String =
    if (r.code == ReasonCodeDto.EXPERIENCE) text(r.code).replace("{x}", exp(r.arg ?: "")) else text(r.code)
