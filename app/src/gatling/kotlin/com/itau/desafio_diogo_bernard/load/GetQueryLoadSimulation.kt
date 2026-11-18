package com.itau.desafio_diogo_bernard.load

import io.gatling.javaapi.core.CoreDsl.constantUsersPerSec
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.listFeeder
import io.gatling.javaapi.core.CoreDsl.rampUsersPerSec
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.OpenInjectionStep
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.io.File

class GetQueryLoadSimulation : Simulation() {
    private val httpRequests = requisicoesCarga()
    private val aquecimento = System.getProperty("load.aquecimento").toBoolean()

    // A base tem menos notas que usuários no 10k: o feeder as reaproveita em círculo.
    private val feeder = listFeeder(linhasFeeder()).circular()
    private val query =
        scenario("E2E-14 consulta isolada")
            .feed(feeder)
            .exec(
                http("GET solicitação")
                    .get("/issuance-requests/#{id}")
                    .header("X-Client-Id", "#{cliente}")
                    .check(status().`is`(200)),
            ).exec(
                http("GET nota fiscal")
                    .get("/invoices/#{numero}?series=1")
                    .header("X-Client-Id", "#{cliente}")
                    .check(status().`is`(200)),
            )

    init {
        setUp(query.injectOpen(injecao()))
            .protocols(protocoloCarga())
            .assertions(global().successfulRequests().percent().shouldBe(100.0))
    }

    // O aquecimento sobe a carga em rampa: a JVM fria não sustenta 1.000 usuários/s de imediato, e uma fila de pedidos
    // esperando conexão do pool de leitura esgota o heap. O feeder circular reaproveita as notas semeadas.
    private fun injecao(): List<OpenInjectionStep> =
        if (aquecimento) {
            listOf(
                rampUsersPerSec(USUARIOS_INICIAIS_AQUECIMENTO).to(USUARIOS_POR_SEGUNDO).during(duracaoRampaAquecimento()),
                constantUsersPerSec(USUARIOS_POR_SEGUNDO).during(duracaoPatamarAquecimento()),
            )
        } else {
            listOf(constantUsersPerSec(USUARIOS_POR_SEGUNDO).during(duracaoCarga(httpRequests)))
        }

    private fun linhasFeeder(): List<Map<String, Any>> =
        File(System.getProperty("load.feeder", "app/build/reports/load/consulta-$httpRequests.csv"))
            .readLines()
            .drop(1)
            .map { line ->
                val fields = line.split(',')
                require(fields.size == 3) { "Linha CSV de consulta deve conter id, numero e cliente" }
                mapOf("id" to fields[0], "numero" to fields[1], "cliente" to fields[2])
            }
}
