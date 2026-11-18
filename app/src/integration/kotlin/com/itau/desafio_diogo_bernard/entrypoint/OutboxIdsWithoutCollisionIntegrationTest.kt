package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import com.itau.desafio_diogo_bernard.repository.OutboxRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertTrue

@Tag("integration")
class OutboxIdsWithoutCollisionIntegrationTest : PostgresIntegrationBase() {
    @Autowired private lateinit var gateway: IssuanceRequestGateway

    @Autowired private lateinit var events: OutboxRepository

    @AfterEach
    fun concluirSolicitacoes(): Unit = aguardarSolicitacoesConcluidas()

    @Test
    fun `ids do outbox gravados pelo comando nativo e pelas entidades nao colidem`() {
        repeat(QUANTIDADE) {
            gateway.registerOnce(novaSolicitacao())
            events.save(eventoDeEntidade().toEntity())
        }

        val total = jdbc.sql("SELECT count(*) FROM outbox").query(Long::class.java).single()
        val withoutCollision = jdbc.sql("SELECT count(*) = count(DISTINCT id) FROM outbox").query(Boolean::class.java).single()
        assertTrue(total >= 2L * QUANTIDADE)
        assertTrue(withoutCollision)
    }

    private fun eventoDeEntidade(): NewOutboxEvent =
        NewOutboxEvent(UUID.randomUUID(), "notafiscal.teste-entidade.v1", UUID.randomUUID().toString(), "{}", null, Instant.now())

    private companion object {
        const val QUANTIDADE = 60
    }
}
