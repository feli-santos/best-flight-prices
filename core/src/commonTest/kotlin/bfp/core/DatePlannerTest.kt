package bfp.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatePlannerTest {
    private val today = LocalDate(2026, 11, 20)

    @Test fun flexibleIsNextTwelveMonthsStartingNextMonth() {
        val m = DatePlanner.months(DateIntent.Flexible, today)
        assertEquals(12, m.size)
        assertEquals(YearMonth(2026, 12), m.first())
        assertEquals(YearMonth(2027, 11), m.last())
    }

    @Test fun rangeCoversOverlappingMonthsInOrder() {
        val m = DatePlanner.months(DateIntent.Range(LocalDate(2027, 1, 20), LocalDate(2027, 3, 2)), today)
        assertEquals(listOf(YearMonth(2027, 1), YearMonth(2027, 2), YearMonth(2027, 3)), m)
    }

    @Test fun rangeIsClampedToTwelveMonthHorizon() {
        val m = DatePlanner.months(DateIntent.Range(LocalDate(2026, 12, 1), LocalDate(2029, 1, 1)), today)
        assertEquals(12, m.size)
    }

    @Test fun exactIsASingleMonth() =
        assertEquals(listOf(YearMonth(2027, 4)), DatePlanner.months(DateIntent.Exact(LocalDate(2027, 4, 10), LocalDate(2027, 4, 20)), today))

    @Test fun stayMatchWithTolerance() {
        val d = LocalDate(2027, 4, 10)
        assertTrue(DatePlanner.matchesStay(d, LocalDate(2027, 4, 20), nights = 10))
        assertTrue(DatePlanner.matchesStay(d, LocalDate(2027, 4, 21), nights = 10))
        assertFalse(DatePlanner.matchesStay(d, LocalDate(2027, 4, 23), nights = 10))
        assertTrue(DatePlanner.matchesStay(d, LocalDate(2027, 5, 30), nights = null))
    }

    @Test fun yearMonthArithmeticAndFormat() {
        assertEquals(YearMonth(2027, 1), YearMonth(2026, 12).next())
        assertEquals("2027-04", YearMonth(2027, 4).toString())
        assertEquals(YearMonth(2027, 4), YearMonth.parse("2027-04"))
    }

    @Test fun invalidRangeRejected() {
        val ex = runCatching { DateIntent.Range(LocalDate(2027, 3, 1), LocalDate(2027, 2, 1)) }
        assertTrue(ex.isFailure)
    }
}
