package bfp.server

import bfp.server.provider.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test fun parsesProviderNumbers() {
        assertEquals(123450L, Money.toMinor("1234.5"))
        assertEquals(123400L, Money.toMinor("1234"))
        assertEquals(123457L, Money.toMinor("1234.567")) // rounds half up at cent
        assertEquals(5L, Money.toMinor("0.05"))
    }

    @Test fun formatsDecimalString() {
        assertEquals("1234.50", Money.format(123450L))
        assertEquals("0.05", Money.format(5L))
        assertEquals("12.00", Money.format(1200L))
    }

    @Test fun rejectsNegativeAndGarbage() {
        assertFailsWith<IllegalArgumentException> { Money.toMinor("-1") }
        assertFailsWith<IllegalArgumentException> { Money.toMinor("abc") }
    }
}
