package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestReadGateway
import com.itau.desafio_diogo_bernard.gateway.postgres.IssuanceRequestProcessing
import com.itau.desafio_diogo_bernard.gateway.postgres.PersistedInvoice
import com.itau.desafio_diogo_bernard.gateway.postgres.ReadPostgresGateway
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestStateRecord
import com.itau.desafio_diogo_bernard.repository.NumberingCounterRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Tag("integration")
class ReadGatewayCoverageIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var requests: IssuanceRequestReadGateway

    @Autowired
    private lateinit var invoices: InvoiceReadGateway

    @Autowired
    private lateinit var processing: IssuanceRequestProcessing

    @Test
    fun `busca leitura na primario quando replica nao tem solicitacao ou nota`() {
        val id = UUID.randomUUID()
        inserirSolicitacao(id, "FALHA")

        assertNull(requests.find(IssuanceRequestId(id.toString())))
        assertNull(requests.find(IssuanceRequestId(UUID.randomUUID().toString())))
        assertNotNull(requests.findConsistent(IssuanceRequestId(id.toString())))
        assertNull(requests.findConsistent(IssuanceRequestId(UUID.randomUUID().toString())))
        val reference = InvoiceReference(FiscalNumber(12), Series(1))
        assertNull(invoices.findConsistent(reference))

        assertNotNull(requests.findConsistent(IssuanceRequestId(id.toString())))
    }

    @Test
    fun `mapeia falha e limita tamanho do motivo persistido`() {
        val id = UUID.randomUUID()
        inserirSolicitacao(id, "FALHA", "falha simulada")
        val incomplete = UUID.randomUUID()
        inserirSolicitacao(incomplete, "FALHA", "motivo parcial")
        jdbc.sql("UPDATE solicitacao SET nota_serie=1 WHERE id=:id").param("id", incomplete).update()
        val anotherIncomplete = UUID.randomUUID()
        inserirSolicitacao(anotherIncomplete, "FALHA", "referencia parcial")
        jdbc.sql("UPDATE solicitacao SET nota_numero=12 WHERE id=:id").param("id", anotherIncomplete).update()
        val issued = UUID.randomUUID()
        inserirSolicitacao(issued, "EMITIDA")
        jdbc.sql("UPDATE solicitacao SET nota_serie=1,nota_numero=12 WHERE id=:id").param("id", issued).update()

        val failure = assertNotNull(processing.lockForIssuance(IssuanceRequestId(id.toString())))
        val seriesWithoutNumber = assertNotNull(processing.lockForIssuance(IssuanceRequestId(incomplete.toString())))
        val numberWithoutSeries = assertNotNull(processing.lockForIssuance(IssuanceRequestId(anotherIncomplete.toString())))
        val issuedLocked = assertNotNull(processing.lockForIssuance(IssuanceRequestId(issued.toString())))
        val partialRequestSeries = assertNotNull(requests.findConsistent(IssuanceRequestId(incomplete.toString())))
        val partialRequest = assertNotNull(requests.findConsistent(IssuanceRequestId(anotherIncomplete.toString())))
        val fullReference = assertNotNull(requests.findConsistent(IssuanceRequestId(issued.toString())))
        val readFailure = assertNotNull(requests.findConsistent(IssuanceRequestId(id.toString())))
        processing.markFailed(IssuanceRequestId(id.toString()), FailureReason("x".repeat(201)))
        val updated = assertNotNull(requests.findConsistent(IssuanceRequestId(id.toString())))

        assertEquals(IssuanceRequestStatus.FALHA, failure.status)
        assertNull(failure.invoiceReference)
        assertNull(seriesWithoutNumber.invoiceReference)
        assertNull(numberWithoutSeries.invoiceReference)
        assertEquals(InvoiceReference(FiscalNumber(12), Series(1)), issuedLocked.invoiceReference)
        assertNull(processing.lockForIssuance(IssuanceRequestId(UUID.randomUUID().toString())))
        assertNull(partialRequestSeries.invoiceReference)
        assertNull(partialRequest.invoiceReference)
        assertEquals(InvoiceReference(FiscalNumber(12), Series(1)), fullReference.invoiceReference)
        assertNull(fullReference.failureReason)
        assertEquals("falha simulada", readFailure.failureReason?.value)
        assertEquals(200, updated.failureReason?.value?.length)
    }

    @Test
    fun `mapeia solicitacao encontrada pela leitura da replica`() {
        val id = UUID.randomUUID()
        val now = Instant.parse("2026-11-14T18:00:00Z")
        val stateRecord =
            IssuanceRequestStateRecord(id, CLIENTE, IssuanceRequestStatus.RECEBIDA, now, now, null, null, null)
        val repository = Mockito.mock(IssuanceRequestRepository::class.java)
        Mockito.`when`(repository.findStatus(id)).thenReturn(stateRecord)
        val gateway =
            ReadPostgresGateway(
                repository,
                Mockito.mock(PersistedInvoice::class.java),
                Mockito.mock(NumberingCounterRepository::class.java),
                NotaFiscalProperties(),
                BusinessMetrics(SimpleMeterRegistry()),
            )

        assertNotNull(gateway.find(IssuanceRequestId(id.toString())))
    }

    private fun inserirSolicitacao(
        id: UUID,
        status: String = "RECEBIDA",
        reason: String? = null,
    ) {
        val now = Timestamp.from(Instant.parse("2026-11-14T18:00:00Z"))
        jdbc
            .sql(
                """INSERT INTO solicitacao
            (id,cliente_id,idempotency_key,impressao_digital,pedido,status,motivo_falha,recebida_em,atualizada_em)
            VALUES (:id,:cliente,:chave,:digital,CAST(:pedido AS jsonb),:status,:motivo,:agora,:agora)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("chave", UUID.randomUUID())
            .param("digital", "a".repeat(64))
            .param("pedido", PEDIDO_GRAVADO)
            .param("status", status)
            .param("motivo", reason)
            .param("agora", now)
            .update()
    }
}
