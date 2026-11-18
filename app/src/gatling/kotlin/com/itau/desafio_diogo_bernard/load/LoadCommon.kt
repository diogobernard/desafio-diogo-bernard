package com.itau.desafio_diogo_bernard.load

import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpProtocolBuilder
import java.time.Duration

const val USUARIOS_POR_SEGUNDO = 1_000.0
const val USUARIOS_INICIAIS_AQUECIMENTO = 10.0
private const val REQUISICOES_MINIMAS = 1_000L
private const val RAMPA_PADRAO_S = "30"
private const val PATAMAR_PADRAO_S = "10"

const val PEDIDO_CARGA = """
    {"recipient":{"person_type":"FISICA","document":"52998224725"},
     "items":[{"code":"SKU-CARGA","description":"Item de carga","unit_price":10.00,"quantity":1}],
     "freight_value":1.00,
     "addresses":[{"purpose":"ENTREGA","region":"SUDESTE"}]}
"""

fun requisicoesCarga(): Long = System.getProperty("load.requests", "1000").toLong().coerceAtLeast(REQUISICOES_MINIMAS)

fun duracaoCarga(httpRequests: Long): Duration = Duration.ofSeconds((httpRequests / REQUISICOES_MINIMAS).coerceAtLeast(1))

fun duracaoRampaAquecimento(): Duration = Duration.ofSeconds(System.getProperty("load.rampa.segundos", RAMPA_PADRAO_S).toLong())

fun duracaoPatamarAquecimento(): Duration = Duration.ofSeconds(System.getProperty("load.patamar.segundos", PATAMAR_PADRAO_S).toLong())

fun clienteCarga(httpRequests: Long): String = System.getProperty("load.client", "carga-$httpRequests")

fun protocoloCarga(): HttpProtocolBuilder {
    val protocol =
        http
            .baseUrl(System.getProperty("api.baseUrl", "http://localhost:8080"))
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
    return if (System.getProperty("load.compartilharConexoes").toBoolean()) protocol.shareConnections() else protocol
}
