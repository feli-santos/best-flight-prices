package bfp.server.provider

import java.math.BigDecimal
import java.math.RoundingMode

/** Prices are carried as minor units (hundredths) and rendered as decimal strings. Never floats. */
object Money {
    fun toMinor(s: String): Long {
        val d = try { BigDecimal(s.trim()) } catch (_: NumberFormatException) { throw IllegalArgumentException("bad amount: $s") }
        require(d.signum() >= 0) { "negative amount: $s" }
        return d.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    fun format(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
}
