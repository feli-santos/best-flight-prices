package bfp.ui.i18n

import bfp.client.ApiFailure
import bfp.contracts.BandDto
import bfp.contracts.ReasonCodeDto
import bfp.contracts.ReasonDto
import bfp.ui.format.FareAge

object EsStrings : Strings {
    override val lang = Lang.ES
    override val appName = "BestFlightPrice"
    override val from = "Saliendo de"
    override val fromHint = "Ciudad o código de aeropuerto"
    override val to = "Hacia"
    override val toHint = "Cualquier lugar, o ciudad, país, continente"
    override val popular = "Lugares populares"
    override val anywhere = "Cualquier lugar"
    override val when_ = "Cuándo"
    override val anytime = "Cualquier fecha"
    override val aMonth = "Un mes"
    override val exactDates = "Fechas exactas"
    override val departHint = "Ida (AAAA-MM-DD)"
    override val returnHint = "Vuelta (opcional)"
    override val stay = "Estadía"
    override val stayAuto = "Sugerir por mí"
    override val nights = { n: Int -> if (n == 1) "1 noche" else "$n noches" }
    override val search = "Buscar vuelos"
    override val searching = "Buscando tarifas…"
    override val exploreTitle = "Destinos más baratos"
    override val planTitle = "Mejor época para ir"
    override val back = "Volver"
    override val retry = "Intentar de nuevo"
    override val dismiss = "Cerrar"
    override val sampleBadge = "Datos de ejemplo"
    override val sampleNote = "Estos precios son inventados, solo para pruebas. No son tarifas reales."
    override val noFare = "No se encontró tarifa"
    override val fareNote = "Tarifa más baja vista recientemente para el mes. Los precios cambian; verifica antes de comprar."
    override val domestic = "Nacional"
    override val bestMonthsLabel = "Mejores meses"
    override val priceFrom = "desde"
    override val pending = "Aún cargando"
    override val options = "Opciones de vuelo"
    override val noOptions = "No se encontraron vuelos para este mes."
    override val book = "Ver este vuelo"
    override val direct = "Directo"
    override val stops = { n: Int -> if (n == 1) "1 escala" else "$n escalas" }
    override val updateRequiredTitle = "Actualización necesaria"
    override val updateRequiredBody = "Esta versión es demasiado antigua para obtener precios. Instala la última versión."
    override val emptyResults = "Todavía no hay nada que mostrar. Elige desde dónde sales y busca."
    override val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    override val cachedLabel = { h: Int? -> if (h == null) "Resultado guardado, estás sin conexión" else "Resultado guardado hace $h h, estás sin conexión" }
    override val fareAgeLabel = { a: FareAge -> when (a) {
        FareAge.JustNow -> "Verificado ahora"; is FareAge.Hours -> "Verificado hace ${a.n} h"; is FareAge.Days -> "Verificado hace ${a.n} días"; FareAge.Unknown -> "" } }
    override val seismicNote = "Han ocurrido terremotos de magnitud 6+ en la zona."
    override val continents = mapOf("Africa" to "África", "Asia" to "Asia", "Europe" to "Europa", "North America" to "Norteamérica", "Oceania" to "Oceanía", "South America" to "Sudamérica")
    override val departLabel = "Ida"
    override val returnLabel = "Vuelta"
    override val invalidDate = "Usa el formato AAAA-MM-DD"

    override fun failure(f: ApiFailure) = when (f) {
        ApiFailure.Offline -> "Estás sin conexión. Revisa tu conexión e inténtalo de nuevo."
        ApiFailure.UpdateRequired -> updateRequiredBody
        is ApiFailure.RateLimited -> "Demasiadas búsquedas. Espera ${f.retryAfterSeconds ?: 60} segundos e inténtalo de nuevo."
        is ApiFailure.ProviderUnavailable -> "Los precios no están disponibles ahora. Inténtalo en unos minutos."
        ApiFailure.ProviderRejected -> "El servicio de precios rechazó la solicitud. Inténtalo más tarde."
        ApiFailure.CurrencyUnsupported -> "Esta moneda no es compatible."
        ApiFailure.InvalidRequest -> "Algo en la búsqueda no es válido. Revisa el aeropuerto y las fechas."
        ApiFailure.Server -> "Algo salió mal de nuestro lado. Inténtalo de nuevo."
    }
    override fun band(b: BandDto) = when (b) { BandDto.GREAT -> "Excelente"; BandDto.GOOD -> "Bueno"; BandDto.MIXED -> "Regular"; BandDto.AVOID -> "Evitar" }
    override fun experience(id: String) = ES_EXPERIENCES[id] ?: id
    override fun reason(r: ReasonDto) = reasonText(r, ::experience) { c -> when (c) {
        ReasonCodeDto.FARE_CHEAPEST -> "Tarifa más barata del año"
        ReasonCodeDto.FARE_LOW -> "Tarifa baja"
        ReasonCodeDto.FARE_HIGH -> "Tarifa alta"
        ReasonCodeDto.FARE_MISSING -> "No se encontró tarifa este mes"
        ReasonCodeDto.CLIMATE_PLEASANT -> "Clima agradable"
        ReasonCodeDto.CLIMATE_HOT -> "Mucho calor"
        ReasonCodeDto.CLIMATE_COLD -> "Mucho frío"
        ReasonCodeDto.CLIMATE_WET -> "Época de lluvias"
        ReasonCodeDto.CLIMATE_HUMID -> "Húmedo"
        ReasonCodeDto.CYCLONE_HIGH -> "Alto riesgo de ciclones"
        ReasonCodeDto.CYCLONE_MODERATE -> "Algún riesgo de ciclones"
        ReasonCodeDto.EXPERIENCE -> "Mejor época para: {x}"
        ReasonCodeDto.STAY_LONG_HAUL -> "Viaje largo: quédate más tiempo para que valga la pena"
        ReasonCodeDto.STAY_TIME_ZONE -> "Gran diferencia horaria: suma una noche para recuperarte"
        ReasonCodeDto.STAY_DESTINATION_MIN -> "La mayoría de los viajeros se queda al menos esto aquí"
    } }
}

private val ES_EXPERIENCES = mapOf(
    "uyuni-mirror" to "el efecto espejo de Uyuni", "japan-sakura" to "los cerezos en flor", "japan-autumn" to "los colores del otoño",
    "hokkaido-snow" to "la nieve y el festival de Sapporo", "aurora-north" to "la aurora boreal", "rio-carnival" to "el Carnaval",
    "europe-christmas" to "los mercados navideños", "oktoberfest" to "la Oktoberfest", "venice-carnival" to "el Carnaval de Venecia",
    "edinburgh-festival" to "los festivales de Edimburgo", "tulips" to "la temporada de tulipanes", "korea-blossom" to "las flores de primavera y los colores del otoño",
    "canada-foliage" to "los colores del otoño", "greek-summer" to "playas e islas", "algarve-summer" to "la temporada de playa",
    "bali-dry" to "la estación seca", "patagonia-summer" to "el trekking en la Patagonia", "patagonia-ski" to "el esquí",
    "cusco-dry" to "Machu Picchu y los treks", "maldives-dry" to "la estación seca", "phuket-dry" to "la estación seca",
    "caribbean-dry" to "la estación seca", "masai-mara" to "la Gran Migración", "tz-dry" to "el safari y los treks",
    "cape-summer" to "el verano", "nz-ski" to "el esquí", "nz-summer" to "el verano", "nepal-trek" to "el trekking",
    "fiji-dry" to "la estación seca", "tahiti-dry" to "la estación seca",
)
