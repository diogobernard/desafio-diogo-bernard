package com.itau.desafio_diogo_bernard.entrypoint.job

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxPublication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OutboxPublisherJobTest {
    private val appProperties = NotaFiscalProperties()

    @Test
    fun `publica cada topico com o lote configurado`() {
        val calls = mutableListOf<Pair<String, Int>>()
        val job =
            OutboxPublisherJob(
                outbox { topic, limit ->
                    calls += topic to limit
                    2
                },
                appProperties,
            )

        job.publishRequests()
        job.publishInvoices()

        assertEquals(
            listOf(
                appProperties.kafka.topicoSolicitacoes to 500,
                appProperties.kafka.topicoNotas to 500,
            ),
            calls,
        )
    }

    @Test
    fun `mantem job ativo quando publicacao falha`() {
        var calls = 0
        val job =
            OutboxPublisherJob(
                outbox { _, _ ->
                    calls++
                    error("falha simulada")
                },
                appProperties,
            )

        job.publishRequests()
        job.publishInvoices()

        assertEquals(2, calls)
    }

    @Test
    fun `inicia e encerra publicador sem falhar`() {
        val job = OutboxPublisherJob(outbox { _, _ -> 0 }, appProperties)

        job.start()
        job.shutdown()
    }

    private fun outbox(action: (String, Int) -> Int): OutboxPublication =
        object : OutboxPublication {
            override fun publishPending(
                topic: String,
                limit: Int,
            ): Int = action(topic, limit)
        }
}
