package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.job.OutboxPublisherJob
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxPublication
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@ExtendWith(OutputCaptureExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "app.datasource.write.hikari.maximum-pool-size=5",
        "app.datasource.write.hikari.minimum-idle=1",
        "app.datasource.read.hikari.maximum-pool-size=3",
        "app.datasource.read.hikari.minimum-idle=1",
        "app.runtime.role=api",
        "nota-fiscal.kafka.topico-solicitacoes=notafiscal.ti23.solicitacoes.v1",
        "nota-fiscal.kafka.topico-notas=notafiscal.ti23.notas.v1",
    ],
)
class OutboxPublisherByTopicIntegrationTest : PostgresIntegrationBase() {
    @Autowired private lateinit var publisher: OutboxPublication

    @Autowired private lateinit var properties: NotaFiscalProperties

    @Test
    fun `publica lote do outbox com uma confirmacao por lote`(output: CapturedOutput) {
        val topic = "notafiscal.ti22.lote.v1"
        jdbc.inserirEventos(topic, QUANTIDADE_LOTE) { """{"n":"${UUID.randomUUID()}"}""" }
        jdbc.inserirEventos(topic, 1) { """{"grande":"${"x".repeat(TAMANHO_ACIMA_DO_LIMITE)}"}""" }
        SqlStatementRecorder.statements.clear()

        val published = publisher.publishPending(topic, LOTE_MAXIMO)

        val commands = SqlStatementRecorder.currentThreadStatements().map { it.trimStart().lowercase() }
        assertEquals(QUANTIDADE_LOTE, published)
        assertEquals(1, commands.count { it.startsWith("update outbox") })
        assertEquals(QUANTIDADE_LOTE, jdbc.contarEventos(topic, published = true))
        assertEquals(1, jdbc.contarEventos(topic, published = false))
        assertEquals(QUANTIDADE_LOTE, KafkaTopicProbe(kafka.bootstrapServers).receberChaves(topic, QUANTIDADE_LOTE).size)
        assertTrue(output.all.contains("tipoErro=RecordTooLargeException"))
        assertFalse(output.all.contains("x".repeat(TRECHO_DO_PAYLOAD)))
    }

    @Test
    fun `publica notas emitidas sem esperar o backlog de solicitacoes`() {
        val requests = properties.kafka.topicoSolicitacoes
        val invoices = properties.kafka.topicoNotas
        val job = OutboxPublisherJob(publisher, properties.copy(outbox = NotaFiscalProperties.Outbox(lote = 1)))
        jdbc.inserirEventos(requests, BACKLOG) { """{"solicitacao_id":"${UUID.randomUUID()}"}""" }
        jdbc.inserirEventos(invoices, 1) { """{"nota":"${UUID.randomUUID()}"}""" }
        val taskScheduler = SimpleAsyncTaskScheduler().apply { setVirtualThreads(true) }

        try {
            taskScheduler.scheduleWithFixedDelay(job::publishRequests, Duration.ofMillis(INTERVALO_MS))
            taskScheduler.scheduleWithFixedDelay(job::publishInvoices, Duration.ofMillis(INTERVALO_MS))
            await.atMost(PRAZO_SEGUNDOS, SECONDS).untilAsserted {
                assertEquals(0, jdbc.contarEventos(invoices, published = false))
            }
            assertTrue(jdbc.contarEventos(requests, published = false) > BACKLOG * MARGEM_DO_BACKLOG)
        } finally {
            taskScheduler.close()
        }
    }

    private companion object {
        const val QUANTIDADE_LOTE = 200
        const val LOTE_MAXIMO = 500
        const val TAMANHO_ACIMA_DO_LIMITE = 1_200_000
        const val TRECHO_DO_PAYLOAD = 100
        const val BACKLOG = 3_000
        const val MARGEM_DO_BACKLOG = 0.8
        const val PRAZO_SEGUNDOS = 15L
        const val INTERVALO_MS = 50L
    }
}
