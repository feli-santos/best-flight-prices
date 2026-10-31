package bfp.ui.format

import kotlin.time.Instant

/** Pure, locale-parametrised formatting shared by every platform (identical output on Android and iOS). */

private fun groupThousands(n: Long, sep: Char): String {
    val s = n.toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(sep)
        sb.append(c)
    }
    return sb.toString()
}

/** Whole-unit price: rounds half up; amount is the contract's decimal string. */
fun formatMoney(amount: String, currency: String, lang: String): String {
    val whole = amount.substringBefore('.')
    val frac = amount.substringAfter('.', "")
    var units = whole.toLongOrNull() ?: return "$amount $currency"
    if (frac.isNotEmpty() && frac[0] >= '5') units++
    val sep = if (lang == "en") ',' else '.'
    val num = groupThousands(units, sep)
    val symbol = when (currency) {
        "BRL" -> "R$"
        "USD" -> if (lang == "en") "$" else "US$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> null
    }
    return when {
        symbol == null -> "$currency $num"
        lang == "en" -> "$symbol$num"
        else -> "$symbol $num"
    }
}

fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0) "${m}m" else "${h}h ${m.toString().padStart(2, '0')}m"
}

/** `months` are the 12 localized short month names. */
fun formatDay(iso: String, months: List<String>, lang: String): String {
    val p = iso.split("-")
    if (p.size != 3) return iso
    val y = p[0].toIntOrNull() ?: return iso
    val m = p[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return iso
    val d = p[2].toIntOrNull() ?: return iso
    return if (lang == "en") "${months[m - 1]} $d, $y" else "$d ${months[m - 1]} $y"
}

fun formatMonthYear(yearMonth: String, months: List<String>): String {
    val p = yearMonth.split("-")
    val m = p.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return yearMonth
    return "${months[m - 1]} ${p[0]}"
}

sealed interface FareAge {
    data object JustNow : FareAge
    data class Hours(val n: Int) : FareAge
    data class Days(val n: Int) : FareAge
    data object Unknown : FareAge
}

fun fareAge(retrievedAtIso: String, nowMs: Long): FareAge {
    val at = runCatching { Instant.parse(retrievedAtIso).toEpochMilliseconds() }.getOrNull() ?: return FareAge.Unknown
    val hours = ((nowMs - at) / 3_600_000L).toInt()
    return when {
        hours < 1 -> FareAge.JustNow
        hours < 48 -> FareAge.Hours(hours)
        else -> FareAge.Days(hours / 24)
    }
}

data class AirportEntry(val iata: String, val name: String, val city: String, val country: String, val size: String)

private fun fold(s: String): String = buildString {
    for (c in s.lowercase()) append(
        when (c) {
            'á', 'à', 'â', 'ã', 'ä' -> 'a'; 'é', 'è', 'ê', 'ë' -> 'e'; 'í', 'ì', 'î', 'ï' -> 'i'
            'ó', 'ò', 'ô', 'õ', 'ö' -> 'o'; 'ú', 'ù', 'û', 'ü' -> 'u'; 'ç' -> 'c'; 'ñ' -> 'n'; else -> c
        },
    )
}

/** Code match first, then city prefix, then name/city contains; large airports first within a tier. */
fun searchAirports(all: List<AirportEntry>, query: String, limit: Int = 8): List<AirportEntry> {
    val q = fold(query.trim())
    if (q.isEmpty()) return emptyList()
    fun tier(a: AirportEntry): Int = when {
        fold(a.iata) == q -> 0
        fold(a.iata).startsWith(q) -> 1
        fold(a.city).startsWith(q) -> 2
        fold(a.city).contains(q) || fold(a.name).contains(q) -> 3
        else -> 9
    }
    return all.map { it to tier(it) }.filter { it.second < 9 }
        .sortedWith(compareBy({ it.second }, { if (it.first.size == "L") 0 else 1 }, { it.first.iata }))
        .take(limit).map { it.first }
}
