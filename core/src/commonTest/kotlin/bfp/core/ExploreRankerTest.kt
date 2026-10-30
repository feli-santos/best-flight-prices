package bfp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExploreRankerTest {
    private fun c(id: String, p: Long?) = CityFare(id, p)

    @Test fun sortsByCheapestCityAndPutsNoFareLast() {
        val r = ExploreRanker.rank(
            originCityId = "br-bhz", originCountry = "BR",
            countries = listOf(
                CountryFares("PT", listOf(c("pt-lis", 3000))),
                CountryFares("AR", listOf(c("ar-bue", 1000), c("ar-cor", 1500))),
                CountryFares("JP", listOf(c("jp-tyo", null))),
            ),
        )
        assertEquals(listOf("AR", "PT", "JP"), r.map { it.countryCode })
        assertNull(r.last().cheapest)
    }

    @Test fun excludesOriginCityAndFlagsDomestic() {
        val r = ExploreRanker.rank("br-bhz", "BR", listOf(
            CountryFares("BR", listOf(c("br-bhz", 100), c("br-sao", 500))),
        ))
        assertEquals(listOf("br-sao"), r.single().cities.map { it.cityId })
        assertTrue(r.single().domestic)
    }

    @Test fun citiesCappedAtThreeAndSortedWithNoFareLast() {
        val r = ExploreRanker.rank("x", "ZZ", listOf(CountryFares("JP", listOf(
            c("a", null), c("b", 900), c("c", 700), c("d", 800), c("e", 600)))))
        assertEquals(listOf("e", "c", "d"), r.single().cities.map { it.cityId })
    }

    @Test fun deterministicTieBreakByCountryCode() {
        val a = ExploreRanker.rank("x", "ZZ", listOf(CountryFares("ES", listOf(c("m", 500))), CountryFares("DE", listOf(c("b", 500)))))
        assertEquals(listOf("DE", "ES"), a.map { it.countryCode })
    }

    @Test fun countryWithOnlyOriginCityIsDropped() {
        val r = ExploreRanker.rank("br-bhz", "BR", listOf(CountryFares("BR", listOf(c("br-bhz", 1)))))
        assertEquals(0, r.size)
    }
}
