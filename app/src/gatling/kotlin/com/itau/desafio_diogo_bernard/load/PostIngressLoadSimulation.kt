package com.itau.desafio_diogo_bernard.load

import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.util.UUID

private const val LATENCIA_MAXIMA_POST_MS = "3000"
private const val PERCENTUAL_ACEITO = 100.0

class PostIngressLoadSimulation : Simulation() {
    private val httpRequests = requisicoesCarga()
    private val maxLatencyMs = System.getProperty("load.post.maxMs", LATENCIA_MAXIMA_POST_MS).toInt()

    private val ingress =
        scenario("Ingresso: POST isolado")
            .exec { session -> session.set("chave", UUID.randomUUID().toString()) }
            .exec(
                http("POST solicitação")
                    .post("/issuance-requests")
                    .header("X-Client-Id", clienteCarga(httpRequests))
                    .header("Idempotency-Key", "#{chave}")
                    .body(StringBody(PEDIDO_CARGA))
                    .check(status().`is`(202), jsonPath("$.id").exists()),
            )

    init {
        setUp(
            ingress.injectOpen(constantUsersPerSec(USUARIOS_POR_SEGUNDO).during(duracaoCarga(httpRequests))),
        ).protocols(protocoloCarga())
            .assertions(
                global().successfulRequests().percent().shouldBe(PERCENTUAL_ACEITO),
                global().responseTime().max().lte(maxLatencyMs),
            )
    }
}
