package com.itau.desafio_diogo_bernard.entrypoint

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.test.web.servlet.client.RestTestClient
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Tag("integration")
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.server.port=0",
        "app.datasource.write.hikari.maximum-pool-size=5",
        "app.datasource.write.hikari.minimum-idle=1",
        "app.datasource.read.hikari.maximum-pool-size=3",
        "app.datasource.read.hikari.minimum-idle=1",
        "nota-fiscal.kafka.topico-solicitacoes=teste.actuator.solicitacoes.v1",
        "nota-fiscal.kafka.topico-solicitacoes-dlq=teste.actuator.solicitacoes.v1.dlq",
        "nota-fiscal.kafka.topico-notas=teste.actuator.notas.v1",
    ],
)
class ActuatorManagementPortIntegrationTest : PostgresIntegrationBase() {
    @LocalManagementPort
    private var managementPort: Int = 0

    private lateinit var management: RestTestClient

    @BeforeEach
    fun conectarGerenciamento() {
        management = RestTestClient.bindToServer().baseUrl("http://localhost:$managementPort").build()
    }

    @Test
    fun `expoe actuator so na porta de gerenciamento`(output: CapturedOutput) {
        CAMINHOS.forEach { path ->
            assertEquals(HttpStatus.OK, status(management, path), "gerenciamento $path")
            assertEquals(HttpStatus.NOT_FOUND, status(http, path), "porta da API $path")
        }

        assertFalse(output.all.contains("/actuator/health"))
    }

    private fun status(
        client: RestTestClient,
        path: String,
    ): HttpStatusCode =
        client
            .get()
            .uri(path)
            .exchange()
            .returnResult(String::class.java)
            .status

    private companion object {
        val CAMINHOS = listOf("/actuator/prometheus", "/actuator/health/readiness", "/actuator/health/liveness")
    }
}
