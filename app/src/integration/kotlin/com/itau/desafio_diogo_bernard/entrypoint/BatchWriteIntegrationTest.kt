package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestRegistration
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxEventRecorder
import com.itau.desafio_diogo_bernard.gateway.postgres.lote.BatchWriter
import com.itau.desafio_diogo_bernard.gateway.postgres.lote.IssuanceRequestBatchPersister
import com.itau.desafio_diogo_bernard.gateway.postgres.lote.PendingWrite
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import com.itau.desafio_diogo_bernard.repository.entity.StoredOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Tag("integration")
class BatchWriteIntegrationTest : PostgresIntegrationBase() {
    @Autowired private lateinit var gateway: IssuanceRequestGateway

    @Autowired private lateinit var persister: IssuanceRequestBatchPersister

    @Autowired private lateinit var writer: BatchWriter

    @Autowired private lateinit var outbox: OutboxEventRecorder

    @Autowired private lateinit var json: JsonMapper

    @AfterEach
    fun concluirSolicitacoes(): Unit = aguardarSolicitacoesConcluidas()

    @Test
    fun `agrupa gravacoes concorrentes em poucos comandos`() {
        SqlStatementRecorder.statements.clear()

        val results = gravarConcorrentes(TOTAL_CONCORRENTE) { novaSolicitacao() }

        val commands = SqlStatementRecorder.statements.count { it.sql.contains("unnest", ignoreCase = true) }
        assertTrue(results.all { it is IssuanceRequestRegistration.Created })
        assertEquals(TOTAL_CONCORRENTE, contar("solicitacao"))
        assertEquals(TOTAL_CONCORRENTE, eventosDeSolicitacao())
        assertTrue(commands in 1 until TOTAL_CONCORRENTE, "Comandos de lote: $commands")
    }

    @Test
    fun `resolve a mesma chave de idempotencia dentro do mesmo lote`() {
        val key = UUID.randomUUID()
        val batch = listOf(insert(key), insert(key), insert(UUID.randomUUID()))

        val createdEntries = persister.persist(batch)

        assertEquals(listOf(true, false, true), createdEntries)
        assertEquals(2, contar("solicitacao"))
        assertEquals(2, eventosDeSolicitacao())
    }

    @Test
    fun `item invalido nao falha os demais itens do lote`() {
        val validItems = listOf(PendingWrite(insert()), PendingWrite(insert()))
        val invalidOne = PendingWrite(insert(order = """{"descricao":"\u0000"}"""))

        writer.write(listOf(validItems[0], invalidOne, validItems[1]))

        assertTrue(validItems.all { it.await(PRAZO) })
        assertFailsWith<DataIntegrityViolationException> { invalidOne.await(PRAZO) }
        assertEquals(2, contar("solicitacao"))
        assertEquals(2, eventosDeSolicitacao())
    }

    private fun gravarConcorrentes(
        total: Int,
        newEntry: () -> IssuanceRequest,
    ): List<IssuanceRequestRegistration> {
        val startGate = CountDownLatch(1)
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val tasks: List<Future<IssuanceRequestRegistration>> =
                List(total) {
                    executor.submit<IssuanceRequestRegistration> {
                        startGate.await()
                        gateway.registerOnce(newEntry())
                    }
                }
            startGate.countDown()
            return tasks.map { it.get() }
        }
    }

    private fun insert(
        key: UUID = UUID.randomUUID(),
        order: String? = null,
    ): IssuanceRequestInsert {
        val issuanceRequest = novaSolicitacao(key)
        val event = outbox.prepareRequest(UUID.fromString(issuanceRequest.id.value), issuanceRequest.receivedAt)
        val body = order ?: json.writeValueAsString(StoredOrder.fromDomain(issuanceRequest.order))
        return IssuanceRequestInsert.fromDomain(issuanceRequest, body, event)
    }

    private fun eventosDeSolicitacao(): Int =
        jdbc
            .sql("SELECT count(*) FROM outbox WHERE topico = 'notafiscal.solicitacoes.v1'")
            .query(Long::class.java)
            .single()
            .toInt()

    private companion object {
        const val TOTAL_CONCORRENTE = 200
        val PRAZO: Duration = Duration.ofSeconds(10)
    }
}
