package com.itau.desafio_diogo_bernard.entrypoint

import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals

@Tag("integration")
class ConsumersIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Test
    fun `cada consumidor recebe uma notificacao por nota com todos os itens`() {
        val requestId = criarSolicitacaoParaConsumidores()
        await.atMost(30, SECONDS).untilAsserted {
            assertEquals(4, contarNotificacoes(requestId))
        }
        val event = payloadEvento(requestId)
        assertEquals(1, Regex("\\\"codigo\\\"\\s*:").findAll(event).count())

        kafkaTemplate.send(TOPICO_NOTAS, requestId, event).get()
        await.atMost(20, SECONDS).untilAsserted {
            assertEquals(4, contarNotificacoes(requestId))
            assertEquals(8, contarEntregas(requestId))
        }
    }

    private fun contarNotificacoes(requestId: String): Int = consultarNotificacoes(SQL_COUNT_NOTIFICATIONS, requestId)

    private fun contarEntregas(requestId: String): Int = consultarNotificacoes(SQL_COUNT_DELIVERIES, requestId)

    private fun consultarNotificacoes(
        sql: String,
        requestId: String,
    ): Int {
        val statement = jdbc.sql(sql)
        statement.param("id", requestId)
        val result = statement.query(Int::class.java)
        return result.single()
    }

    private fun payloadEvento(requestId: String): String {
        val statement = jdbc.sql("SELECT payload::text FROM outbox WHERE chave=:id AND topico=:topico")
        statement.param("id", requestId)
        statement.param("topico", TOPICO_NOTAS)
        val result = statement.query(String::class.java)
        return result.single()
    }

    private companion object {
        const val TOPICO_NOTAS = "notafiscal.notas.v1"
        const val SQL_COUNT_NOTIFICATIONS =
            "SELECT count(*) FROM notificacao_recebida WHERE evento_id IN (SELECT evento_id FROM outbox WHERE chave=:id)"
        const val SQL_COUNT_DELIVERIES =
            "SELECT sum(entregas) FROM notificacao_recebida WHERE evento_id IN (SELECT evento_id FROM outbox WHERE chave=:id)"
    }
}
