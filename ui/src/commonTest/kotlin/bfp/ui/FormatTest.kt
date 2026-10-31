package bfp.ui

import bfp.ui.format.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test fun moneyUsesLanguageSeparatorsAndRoundsToWholeUnits() {
        assertEquals("R$ 11.352", formatMoney("11351.74", "BRL", "pt"))
        assertEquals("R$11,352", formatMoney("11351.74", "BRL", "en"))
        assertEquals("R$ 1.234.568", formatMoney("1234567.5", "BRL", "es"))
    }

    @Test fun roundingHalfUpAndSmallValues() {
        assertEquals("R$ 1", formatMoney("0.50", "BRL", "pt"))
        assertEquals("R$ 0", formatMoney("0.49", "BRL", "pt"))
        assertEquals("R$ 999", formatMoney("999", "BRL", "pt"))
        assertEquals("R$ 1.000", formatMoney("999.5", "BRL", "pt"))
    }

    @Test fun currencySymbolsAndFallbackToCode() {
        assertEquals("$1,234", formatMoney("1234", "USD", "en"))
        assertEquals("US$ 1.234", formatMoney("1234", "USD", "pt"))
        assertEquals("€1,234", formatMoney("1234", "EUR", "en"))
        assertEquals("CAD 1,234", formatMoney("1234", "CAD", "en"))
        assertEquals("CAD 1.234", formatMoney("1234", "CAD", "es"))
    }

    @Test fun garbageAmountIsReturnedAsIs() = assertEquals("abc BRL", formatMoney("abc", "BRL", "en"))

    @Test fun durations() {
        assertEquals("8h 05m", formatDuration(485))
        assertEquals("25h 00m", formatDuration(1500))
        assertEquals("45m", formatDuration(45))
    }

    @Test fun days() {
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        assertEquals("Apr 10, 2027", formatDay("2027-04-10", months, "en"))
        assertEquals("10 Apr 2027", formatDay("2027-04-10", months, "pt"))
        assertEquals("junk", formatDay("junk", months, "en"))
    }

    @Test fun monthYear() = assertEquals("Apr 2027", formatMonthYear("2027-04", listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")))

    @Test fun fareAge() {
        val now = 1_793_361_600_000L
        assertEquals(FareAge.JustNow, fareAge("2026-10-30T11:30:00Z", now))
        assertEquals(FareAge.Hours(5), fareAge("2026-10-30T07:00:00Z", now))
        assertEquals(FareAge.Days(3), fareAge("2026-10-27T12:00:00Z", now))
        assertEquals(FareAge.Unknown, fareAge("garbage", now))
        assertEquals(FareAge.JustNow, fareAge("2026-10-31T00:00:00Z", now)) // clock skew: future => just now
    }

    @Test fun airportSearchRanksCodeThenCityAndCapsResults() {
        val airports = listOf(
            AirportEntry("CNF", "Tancredo Neves International", "Belo Horizonte", "BR", "L"),
            AirportEntry("PLU", "Pampulha", "Belo Horizonte", "BR", "M"),
            AirportEntry("GRU", "Guarulhos", "São Paulo", "BR", "L"),
            AirportEntry("BHX", "Birmingham", "Birmingham", "GB", "L"),
        )
        assertEquals(listOf("CNF"), searchAirports(airports, "cnf").map { it.iata })
        assertEquals(listOf("CNF", "PLU"), searchAirports(airports, "belo").map { it.iata })
        assertEquals(listOf("GRU"), searchAirports(airports, "sao paulo").map { it.iata }) // accent-insensitive
        assertEquals(emptyList(), searchAirports(airports, ""))
        assertEquals(1, searchAirports(airports, "b", limit = 1).size)
    }
}
