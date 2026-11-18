package com.itau.desafio_diogo_bernard.entrypoint

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.test.web.servlet.client.expectBody
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@ExtendWith(OutputCaptureExtension::class)
class ObservabilityIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var exporter: InMemorySpanExporter

    @BeforeEach
    fun limparSpans(): Unit = exporter.reset()

    @Test
    fun `propaga trace da requisicao ate os quatro consumidores`() {
        val id = enviarSolicitacao()
        val traceparent =
            jdbc
                .sql("SELECT traceparent FROM outbox WHERE chave=:id")
                .param("id", id)
                .query(String::class.java)
                .single()

        await.atMost(30, SECONDS).untilAsserted { assertEquals(4, notifications(id)) }

        val spans = exporter.finishedSpanItems
        val requestSpans =
            spans.filter {
                it.attributes.get(AttributeKey.stringKey(ATRIBUTO_SOLICITACAO)) == id
            }
        assertEquals(traceparent.substring(3, 35), requestSpans.first().traceId)
        assertTrue(requestSpans.map { it.name }.contains("notafiscal.worker"))
        assertEquals(4, requestSpans.count { it.name.startsWith("notafiscal.consumidor.") })
    }

    @Test
    fun `logs nao expoem documento sem mascara`(output: CapturedOutput) {
        val id = enviarSolicitacao()

        await.atMost(30, SECONDS).untilAsserted { assertEquals(4, notifications(id)) }
        assertEquals(HttpStatus.OK, consultarHealth("liveness"))
        assertEquals(HttpStatus.OK, consultarHealth("readiness"))

        assertFalse(output.all.contains(CPF_TESTE))
        assertFalse(output.all.contains("/actuator/health"))
        assertTrue(output.all.contains("Solicitação recebida"))
        assertTrue(output.all.contains("@timestamp"))
    }

    private fun enviarSolicitacao(): String {
        val response =
            http
                .post()
                .uri("/issuance-requests")
                .headers { it.addAll(headers(UUID.randomUUID().toString())) }
                .body(PAYLOAD)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.ACCEPTED)
                .expectBody<String>()
                .returnResult()
                .responseBody
                .orEmpty()
        return idResposta(response)
    }

    private fun consultarHealth(probe: String): HttpStatusCode =
        http
            .get()
            .uri("/actuator/health/$probe")
            .exchange()
            .returnResult(String::class.java)
            .status

    private fun notifications(id: String): Int =
        jdbc
            .sql(
                "SELECT count(*) FROM notificacao_recebida n JOIN outbox o ON n.evento_id=o.evento_id WHERE o.chave=:id",
            ).param("id", id)
            .query(Long::class.java)
            .single()
            .toInt()

    private companion object {
        const val ATRIBUTO_SOLICITACAO = "solicitacao_id"
        const val CPF_TESTE = "52998224725"
    }
}
