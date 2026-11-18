package com.itau.desafio_diogo_bernard.entrypoint

import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
@TestPropertySource(
    properties = [
        "nota-fiscal.consumidores.financeiro.taxa-falha=1.0",
        "nota-fiscal.consumidores.financeiro.tentativas=2",
        "nota-fiscal.consumidores.financeiro.latencia-minima=1ms",
        "nota-fiscal.consumidores.financeiro.latencia-maxima=1ms",
        "nota-fiscal.consumidores.entrega.latencia-minima=2s",
        "nota-fiscal.consumidores.entrega.latencia-maxima=2s",
        "nota-fiscal.kafka.topico-solicitacoes=teste.falha-consumidor.solicitacoes.v1",
        "nota-fiscal.kafka.topico-solicitacoes-dlq=teste.falha-consumidor.solicitacoes.v1.dlq",
        "nota-fiscal.kafka.topico-notas=teste.falha-consumidor.notas.v1",
    ],
)
class ConsumerFailureIntegrationTest : PostgresIntegrationBase() {
    @Test
    fun `falha persistente em um consumidor vai para sua DLQ sem afetar os outros`() {
        val requestId = criarSolicitacaoParaConsumidores()
        await.atMost(30, SECONDS).untilAsserted {
            assertEquals(2, notificacoesDoConsumidor(requestId, "estoque") + notificacoesDoConsumidor(requestId, "registro"))
            assertEquals(0, notificacoesFinanceiro())
        }
        await.atMost(10, SECONDS).untilAsserted {
            assertEquals(3, notificacoesDoEvento(requestId))
        }
        val inDlq =
            KafkaDlqProbe(PostgresIntegrationBase.kafka.getBootstrapServers())
                .solicitacoesRecebidas(TOPICO_FINANCEIRO_DLQ, setOf(requestId))
        assertTrue(requestId in inDlq, "A notificação do financeiro não chegou à DLQ")
    }

    private fun notificacoesDoEvento(requestId: String): Int {
        val statement = jdbc.sql(SQL_COUNT_NOTIFICATIONS)
        statement.param("id", requestId)
        return statement.query(Int::class.java).single()
    }

    private fun notificacoesDoConsumidor(
        requestId: String,
        consumer: String,
    ): Int {
        val statement = jdbc.sql(SQL_COUNT_CONSUMER_NOTIFICATIONS)
        statement.param("id", requestId)
        statement.param("consumidor", consumer)
        return statement.query(Int::class.java).single()
    }

    private fun notificacoesFinanceiro(): Int =
        jdbc
            .sql("SELECT count(*) FROM notificacao_recebida WHERE consumidor='financeiro'")
            .query(Int::class.java)
            .single()

    private companion object {
        const val TOPICO_FINANCEIRO_DLQ = "teste.falha-consumidor.notas.v1.financeiro.dlq"
        const val SQL_COUNT_NOTIFICATIONS =
            "SELECT count(*) FROM notificacao_recebida WHERE evento_id IN (SELECT evento_id FROM outbox WHERE chave=:id)"
        const val SQL_COUNT_CONSUMER_NOTIFICATIONS =
            "SELECT count(*) FROM notificacao_recebida WHERE consumidor=:consumidor AND evento_id IN " +
                "(SELECT evento_id FROM outbox WHERE chave=:id)"
    }
}
