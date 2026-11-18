package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestRegistration
import com.itau.desafio_diogo_bernard.gateway.postgres.IssuanceRequestPostgresGateway
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxEventRecorder
import com.itau.desafio_diogo_bernard.gateway.postgres.lote.IssuanceRequestWrite
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Tag("integration")
class IssuanceRequestOutboxAtomicIntegrationTest : PostgresIntegrationBase() {
    @Autowired private lateinit var gateway: IssuanceRequestGateway

    @Autowired private lateinit var requests: IssuanceRequestRepository

    @Autowired private lateinit var json: JsonMapper

    @Autowired private lateinit var properties: NotaFiscalProperties

    @Autowired private lateinit var writing: IssuanceRequestWrite

    @AfterEach
    fun concluirSolicitacoes(): Unit = aguardarSolicitacoesConcluidas()

    @Test
    fun `nao grava a solicitacao quando o insert do outbox falha no comando unico`() {
        val adapter = IssuanceRequestPostgresGateway(requests, json, outboxComPayloadInvalido(TOPICO_ENTIDADE), writing)

        assertFailsWith<RuntimeException> { adapter.registerOnce(novaSolicitacao()) }

        assertEquals(0, contar("solicitacao"))
        assertEquals(0, contar("outbox"))
    }

    @Test
    fun `grava solicitacao e outbox em um unico comando`() {
        SqlStatementRecorder.statements.clear()

        val result = gateway.registerOnce(novaSolicitacao())

        val writes = insercoesDeSolicitacao()
        assertIs<IssuanceRequestRegistration.Created>(result)
        assertEquals(1, writes.size)
        assertTrue(writes.single().contains("insert into outbox", ignoreCase = true))
        assertEquals(1, contar("solicitacao"))
        assertEquals(1, eventosDeSolicitacao())
    }

    @Test
    fun `com chave repetida nao grava outbox e segue o SELECT da existente`() {
        val key = UUID.randomUUID()
        assertIs<IssuanceRequestRegistration.Created>(gateway.registerOnce(novaSolicitacao(key)))
        SqlStatementRecorder.statements.clear()

        val repeated = gateway.registerOnce(novaSolicitacao(key))

        val reads = SqlStatementRecorder.currentThreadStatements()
        assertIs<IssuanceRequestRegistration.Existing>(repeated)
        assertEquals(1, insercoesDeSolicitacao().size)
        assertTrue(reads.any { it.trimStart().startsWith("select", ignoreCase = true) })
        assertEquals(1, contar("solicitacao"))
        assertEquals(1, eventosDeSolicitacao())
    }

    private fun insercoesDeSolicitacao(): List<String> =
        SqlStatementRecorder.statements.map { it.sql }.filter { it.contains("insert into solicitacao", ignoreCase = true) }

    private fun eventosDeSolicitacao(): Int =
        jdbc
            .sql("SELECT count(*) FROM outbox WHERE topico = :topico")
            .param("topico", properties.kafka.topicoSolicitacoes)
            .query(Long::class.java)
            .single()
            .toInt()

    private companion object {
        const val TOPICO_ENTIDADE = "notafiscal.teste-entidade.v1"
    }
}
