package com.itau.desafio_diogo_bernard.entrypoint

import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.springframework.test.context.TestPropertySource
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals

// Mesmas propriedades do FalhaConsumidorIntegrationTest, para reaproveitar o contexto em cache.
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
class ConsumerBatchFailureIntegrationTest : PostgresIntegrationBase() {
    @Test
    fun `falhas de varias notas no mesmo lote vao todas para a DLQ do consumidor`() {
        val requests = List(QUANTIDADE) { criarSolicitacaoParaConsumidores() }.toSet()

        val inDlq =
            KafkaDlqProbe(PostgresIntegrationBase.kafka.getBootstrapServers())
                .solicitacoesRecebidas(TOPICO_FINANCEIRO_DLQ, requests)

        assertEquals(requests, requests.intersect(inDlq), "Notificações do financeiro ausentes da DLQ")
        await.atMost(30, SECONDS).untilAsserted {
            assertEquals(QUANTIDADE * CONSUMIDORES_SEM_FALHA, notificacoesSemFalha())
        }
    }

    private fun notificacoesSemFalha(): Int =
        jdbc
            .sql("SELECT count(*) FROM notificacao_recebida WHERE consumidor IN ('estoque','registro','entrega')")
            .query(Int::class.java)
            .single()

    private companion object {
        const val QUANTIDADE = 40
        const val CONSUMIDORES_SEM_FALHA = 3
        const val TOPICO_FINANCEIRO_DLQ = "teste.falha-consumidor.notas.v1.financeiro.dlq"
    }
}
