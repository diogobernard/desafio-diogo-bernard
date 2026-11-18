package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxPublication
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals

@Tag("integration")
class PublisherWithoutTraceIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var publisher: OutboxPublication

    @Test
    fun `publica evento sem traceparent e marca outbox como publicada`() {
        val eventId = UUID.randomUUID()
        val requestId = UUID.randomUUID().toString()
        val payload = """{"evento_id":"$eventId","versao":1,"ocorrido_em":"2026-11-14T18:00:00Z","solicitacao_id":"$requestId"}"""
        jdbc
            .sql(
                """INSERT INTO outbox (evento_id,topico,chave,payload,traceparent,criado_em)
            VALUES (:evento,'notafiscal.solicitacoes.v1',:chave,CAST(:payload AS jsonb),NULL,:agora)""",
            ).param("evento", eventId)
            .param("chave", requestId)
            .param("payload", payload)
            .param("agora", Timestamp.from(Instant.parse("2026-11-14T18:00:00Z")))
            .update()

        publisher.publishPending(TOPICO, 1)
        aguardarPublicada(eventId)
    }

    @Test
    fun `extrai contexto quando evento tem traceparent valido`() {
        val eventId = UUID.randomUUID()
        val requestId = UUID.randomUUID().toString()
        val payload = """{"evento_id":"$eventId","versao":1,"ocorrido_em":"2026-11-14T18:00:00Z","solicitacao_id":"$requestId"}"""
        jdbc
            .sql(
                """INSERT INTO outbox (evento_id,topico,chave,payload,traceparent,criado_em)
            VALUES (:evento,'notafiscal.solicitacoes.v1',:chave,CAST(:payload AS jsonb),:traceparent,:agora)""",
            ).param("evento", eventId)
            .param("chave", requestId)
            .param("payload", payload)
            .param("traceparent", "00-1234567890abcdef1234567890abcdef-1234567890abcdef-01")
            .param("agora", Timestamp.from(Instant.parse("2026-11-14T18:00:00Z")))
            .update()

        publisher.publishPending(TOPICO, 1)
        aguardarPublicada(eventId)
    }

    private companion object {
        const val TOPICO = "notafiscal.solicitacoes.v1"
    }

    private fun aguardarPublicada(eventId: UUID) {
        await.atMost(10, SECONDS).untilAsserted {
            assertEquals(
                1,
                jdbc
                    .sql(
                        "SELECT count(*) FROM outbox WHERE evento_id=:evento AND publicado_em IS NOT NULL",
                    ).param("evento", eventId)
                    .query(Int::class.java)
                    .single(),
            )
        }
    }
}
