package bfp.ui.i18n

import bfp.client.ApiFailure
import bfp.contracts.BandDto
import bfp.contracts.ReasonCodeDto
import bfp.contracts.ReasonDto
import bfp.ui.format.FareAge

object PtStrings : Strings {
    override val lang = Lang.PT
    override val appName = "BestFlightPrice"
    override val from = "Saindo de"
    override val fromHint = "Cidade ou código do aeroporto"
    override val to = "Para"
    override val toHint = "Qualquer lugar, ou cidade, país, continente"
    override val popular = "Lugares populares"
    override val anywhere = "Qualquer lugar"
    override val when_ = "Quando"
    override val anytime = "Qualquer data"
    override val aMonth = "Um mês"
    override val exactDates = "Datas exatas"
    override val departHint = "Ida (AAAA-MM-DD)"
    override val returnHint = "Volta (opcional)"
    override val stay = "Estadia"
    override val stayAuto = "Sugerir para mim"
    override val nights = { n: Int -> if (n == 1) "1 noite" else "$n noites" }
    override val search = "Buscar voos"
    override val searching = "Buscando tarifas…"
    override val exploreTitle = "Destinos mais baratos"
    override val planTitle = "Melhor época para ir"
    override val back = "Voltar"
    override val retry = "Tentar de novo"
    override val dismiss = "Fechar"
    override val sampleBadge = "Dados de exemplo"
    override val sampleNote = "Estes preços são inventados, só para teste. Não são tarifas reais."
    override val noFare = "Nenhuma tarifa encontrada"
    override val fareNote = "Menor tarifa vista recentemente para o mês. Os preços mudam; confira antes de comprar."
    override val domestic = "Nacional"
    override val bestMonthsLabel = "Melhores meses"
    override val priceFrom = "a partir de"
    override val pending = "Ainda carregando"
    override val options = "Opções de voo"
    override val noOptions = "Nenhum voo encontrado para este mês."
    override val book = "Ver este voo"
    override val direct = "Direto"
    override val stops = { n: Int -> if (n == 1) "1 parada" else "$n paradas" }
    override val updateRequiredTitle = "Atualização necessária"
    override val updateRequiredBody = "Esta versão é antiga demais para buscar preços. Instale a versão mais recente."
    override val emptyResults = "Nada para mostrar ainda. Escolha de onde sai e busque."
    override val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    override val cachedLabel = { h: Int? -> if (h == null) "Resultado salvo, você está offline" else "Resultado salvo há $h h, você está offline" }
    override val fareAgeLabel = { a: FareAge -> when (a) {
        FareAge.JustNow -> "Verificado agora"; is FareAge.Hours -> "Verificado há ${a.n} h"; is FareAge.Days -> "Verificado há ${a.n} dias"; FareAge.Unknown -> "" } }
    override val seismicNote = "Já houve terremotos de magnitude 6+ na região."
    override val continents = mapOf("Africa" to "África", "Asia" to "Ásia", "Europe" to "Europa", "North America" to "América do Norte", "Oceania" to "Oceania", "South America" to "América do Sul")
    override val departLabel = "Ida"
    override val returnLabel = "Volta"
    override val invalidDate = "Use o formato AAAA-MM-DD"

    override fun failure(f: ApiFailure) = when (f) {
        ApiFailure.Offline -> "Você está offline. Verifique a conexão e tente de novo."
        ApiFailure.UpdateRequired -> updateRequiredBody
        is ApiFailure.RateLimited -> "Muitas buscas. Espere ${f.retryAfterSeconds ?: 60} segundos e tente de novo."
        is ApiFailure.ProviderUnavailable -> "Os preços estão indisponíveis agora. Tente de novo em alguns minutos."
        ApiFailure.ProviderRejected -> "O serviço de preços recusou o pedido. Tente mais tarde."
        ApiFailure.CurrencyUnsupported -> "Esta moeda não é suportada."
        ApiFailure.InvalidRequest -> "Algo na busca não é válido. Confira o aeroporto e as datas."
        ApiFailure.Server -> "Algo deu errado do nosso lado. Tente de novo."
    }
    override fun band(b: BandDto) = when (b) { BandDto.GREAT -> "Ótimo"; BandDto.GOOD -> "Bom"; BandDto.MIXED -> "Regular"; BandDto.AVOID -> "Evitar" }
    override fun experience(id: String) = PT_EXPERIENCES[id] ?: id
    override fun reason(r: ReasonDto) = reasonText(r, ::experience) { c -> when (c) {
        ReasonCodeDto.FARE_CHEAPEST -> "Tarifa mais barata do ano"
        ReasonCodeDto.FARE_LOW -> "Tarifa baixa"
        ReasonCodeDto.FARE_HIGH -> "Tarifa alta"
        ReasonCodeDto.FARE_MISSING -> "Nenhuma tarifa encontrada neste mês"
        ReasonCodeDto.CLIMATE_PLEASANT -> "Clima agradável"
        ReasonCodeDto.CLIMATE_HOT -> "Muito quente"
        ReasonCodeDto.CLIMATE_COLD -> "Muito frio"
        ReasonCodeDto.CLIMATE_WET -> "Época de chuvas"
        ReasonCodeDto.CLIMATE_HUMID -> "Úmido"
        ReasonCodeDto.CYCLONE_HIGH -> "Alto risco de ciclones"
        ReasonCodeDto.CYCLONE_MODERATE -> "Algum risco de ciclones"
        ReasonCodeDto.EXPERIENCE -> "Melhor época para: {x}"
        ReasonCodeDto.STAY_LONG_HAUL -> "Viagem longa: fique mais tempo para valer a pena"
        ReasonCodeDto.STAY_TIME_ZONE -> "Grande diferença de fuso: some uma noite para se recuperar"
        ReasonCodeDto.STAY_DESTINATION_MIN -> "A maioria dos viajantes fica pelo menos isso aqui"
    } }
}

private val PT_EXPERIENCES = mapOf(
    "uyuni-mirror" to "o efeito espelho de Uyuni", "japan-sakura" to "as cerejeiras em flor", "japan-autumn" to "as cores do outono",
    "hokkaido-snow" to "neve e o festival de Sapporo", "aurora-north" to "aurora boreal", "rio-carnival" to "o Carnaval",
    "europe-christmas" to "mercados de Natal", "oktoberfest" to "a Oktoberfest", "venice-carnival" to "o Carnaval de Veneza",
    "edinburgh-festival" to "os festivais de Edimburgo", "tulips" to "a temporada de tulipas", "korea-blossom" to "flores da primavera e cores do outono",
    "canada-foliage" to "as cores do outono", "greek-summer" to "praias e ilhas", "algarve-summer" to "temporada de praia",
    "bali-dry" to "a estação seca", "patagonia-summer" to "trilhas na Patagônia", "patagonia-ski" to "esqui",
    "cusco-dry" to "Machu Picchu e trilhas", "maldives-dry" to "a estação seca", "phuket-dry" to "a estação seca",
    "caribbean-dry" to "a estação seca", "masai-mara" to "a Grande Migração", "tz-dry" to "safári e trilhas",
    "cape-summer" to "o verão", "nz-ski" to "esqui", "nz-summer" to "o verão", "nepal-trek" to "trilhas",
    "fiji-dry" to "a estação seca", "tahiti-dry" to "a estação seca",
)
