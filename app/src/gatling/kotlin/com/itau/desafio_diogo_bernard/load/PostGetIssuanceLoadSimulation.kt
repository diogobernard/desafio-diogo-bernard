package com.itau.desafio_diogo_bernard.load

import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.doIf
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.util.UUID

class PostGetIssuanceLoadSimulation : Simulation() {
    private val httpRequests = requisicoesCarga()

    private val issuance =
        scenario("E2E-08 emissão e GET sob carga")
            .exec { session -> session.set("chave", UUID.randomUUID().toString()) }
            .exec(
                http("POST solicitação")
                    .post("/issuance-requests")
                    .header("X-Client-Id", clienteCarga(httpRequests))
                    .header("Idempotency-Key", "#{chave}")
                    .body(StringBody(PEDIDO_CARGA))
                    .check(status().`is`(202), jsonPath("$.id").saveAs("id")),
            ).exec(
                doIf { session -> session.contains("id") }.then(
                    http("GET imediato da solicitação")
                        .get("/issuance-requests/#{id}")
                        .header("X-Client-Id", clienteCarga(httpRequests))
                        .check(status().`is`(200)),
                ),
            )

    init {
        setUp(
            issuance.injectOpen(constantUsersPerSec(USUARIOS_POR_SEGUNDO).during(duracaoCarga(httpRequests))),
        ).protocols(protocoloCarga())
            .assertions(global().successfulRequests().percent().shouldBe(100.0))
    }
}
