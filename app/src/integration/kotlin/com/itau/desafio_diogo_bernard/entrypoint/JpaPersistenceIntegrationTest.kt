package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.gateway.postgres.IssuanceRequestProcessingRepository
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.repository.InvoiceItemRepository
import com.itau.desafio_diogo_bernard.repository.InvoiceRepository
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.ReceivedNotificationRepository
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceEntity
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceId
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceItemEntity
import com.itau.desafio_diogo_bernard.repository.entity.InvoiceItemId
import com.itau.desafio_diogo_bernard.repository.entity.IssuanceRequestEntity
import com.itau.desafio_diogo_bernard.repository.entity.NumberingCounterEntity
import com.itau.desafio_diogo_bernard.repository.entity.OutboxEntity
import com.itau.desafio_diogo_bernard.repository.entity.ReceivedNotificationEntity
import com.itau.desafio_diogo_bernard.repository.entity.ReceivedNotificationId
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice
import jakarta.persistence.EntityManager
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.util.Optional
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

@Tag("integration")
class JpaPersistenceIntegrationTest : PostgresIntegrationBase() {
    @Autowired private lateinit var entityManager: EntityManager

    @Autowired private lateinit var requests: IssuanceRequestRepository

    @Autowired private lateinit var invoices: InvoiceRepository

    @Autowired private lateinit var items: InvoiceItemRepository

    @Autowired private lateinit var notifications: ReceivedNotificationRepository

    @Autowired private lateinit var sessionFactory: SessionFactory

    @Autowired
    @Qualifier("dataSource")
    private lateinit var primaryDataSource: DataSource

    @Autowired private lateinit var transactionManager: JpaTransactionManager

    @Autowired private lateinit var json: JsonMapper

    @Test
    fun `EntityManagerFactory e JpaTransactionManager usam datasource primario roteado`() {
        assertSame(primaryDataSource, transactionManager.dataSource)
    }

    @Test
    @Transactional
    fun `persiste entidades com id atribuido sem SELECT previo`() {
        SqlStatementRecorder.statements.clear()
        sessionFactory.statistics.clear()
        val issuanceRequest = IssuanceRequestEntity(order = PEDIDO_GRAVADO)
        requests.save(issuanceRequest)
        entityManager.flush()
        val invoice =
            InvoiceEntity(
                InvoiceId(1, 1),
                requestId = issuanceRequest.requestId,
            )
        invoices.save(invoice)
        entityManager.flush()
        items.save(InvoiceItemEntity(InvoiceItemId(1, 1, 1)))
        notifications.save(ReceivedNotificationEntity(ReceivedNotificationId("estoque", UUID.randomUUID())))
        entityManager.flush()
        assertFalse(
            SqlStatementRecorder.currentThreadStatements().any {
                it.trimStart().startsWith("select", ignoreCase = true)
            },
        )
        assertEquals(4, sessionFactory.statistics.entityInsertCount)
    }

    @Test
    @Transactional
    fun `insere itens da nota em lote`() {
        val issuanceRequest = requests.save(IssuanceRequestEntity(order = PEDIDO_GRAVADO))
        invoices.save(InvoiceEntity(InvoiceId(1, 1), requestId = issuanceRequest.requestId))
        entityManager.flush()
        sessionFactory.statistics.clear()
        SqlStatementRecorder.statements.clear()
        items.saveAll((1..500).map { InvoiceItemEntity(InvoiceItemId(1, 1, it)) })
        entityManager.flush()
        assertEquals(500, sessionFactory.statistics.entityInsertCount)
        val inserts =
            SqlStatementRecorder
                .currentThreadStatements()
                .count { it.contains("insert into nota_fiscal_item", ignoreCase = true) }
        assertFalse(inserts >= 500)
    }

    @Test
    @Transactional
    fun `mapeia entidades e ids atribuidos pela aplicacao`() {
        val domain =
            IssuanceRequest(
                IssuanceRequestId(UUID.randomUUID().toString()),
                ClientId(CLIENTE),
                IdempotencyKey(UUID.randomUUID().toString()),
                Fingerprint("a".repeat(64)),
                json.readValue(PAYLOAD, IssueInvoiceRequest::class.java).toDomain(),
                IssuanceRequestStatus.RECEBIDA,
                Instant.EPOCH,
                Instant.EPOCH,
            )
        val issuanceRequest = IssuanceRequestEntity.fromDomain(domain, json)
        assertEquals(UUID.fromString(domain.id.value), issuanceRequest.id)
        assertEquals(domain, issuanceRequest.toDomain(json))
        val domainWithReference =
            domain.copy(
                status = IssuanceRequestStatus.FALHA,
                invoiceReference = InvoiceReference(FiscalNumber(9), Series(1)),
                failureReason = FailureReason("falha fictícia"),
            )
        val requestWithReference = IssuanceRequestEntity.fromDomain(domainWithReference, json)
        assertEquals(1, requestWithReference.invoiceSeries)
        assertEquals(9, requestWithReference.invoiceNumber)
        assertEquals("falha fictícia", requestWithReference.failureReason)

        val invoiceId = InvoiceId(1, 9)
        val invoice = InvoiceEntity(invoiceKey = invoiceId)
        assertEquals(invoiceId, invoice.id)
        val itemId = InvoiceItemId(1, 9, 1)
        val item = InvoiceItemEntity(itemKey = itemId)
        assertEquals(itemId, item.id)
        val notificationId = ReceivedNotificationId("estoque", UUID.randomUUID())
        val notification = ReceivedNotificationEntity(notificationKey = notificationId)
        assertEquals(notificationId, notification.id)
        val outbox = OutboxEntity(id = 88)
        assertEquals(88L, outbox.getId())

        val counter = entityManager.find(NumberingCounterEntity::class.java, 1)
        assertEquals(1, counter.series)
        assertEquals(0, counter.lastNumber)
    }

    @Test
    fun `trata solicitacao ausente depois de iniciar e ao marcar nota`() {
        val id = UUID.randomUUID()
        val repository = Mockito.mock(IssuanceRequestRepository::class.java)
        Mockito
            .`when`(
                repository.start(
                    Mockito.any(UUID::class.java) ?: UUID(0, 0),
                    Mockito.any(Instant::class.java) ?: Instant.EPOCH,
                ),
            ).thenReturn(1)
        Mockito.`when`(repository.findById(id)).thenReturn(Optional.empty())
        val adapter = IssuanceRequestProcessingRepository(repository, json, Clock.systemUTC())

        assertEquals(null, adapter.start(IssuanceRequestId(id.toString())))
        val invoice = notaParaSolicitacao(id)
        assertFailsWith<IllegalArgumentException> { adapter.markIssued(invoice) }
    }

    private fun notaParaSolicitacao(id: UUID): Invoice {
        val order = json.readValue(PAYLOAD, IssueInvoiceRequest::class.java).toDomain()
        val calculated = CalculateInvoice().calculate(order, TaxationMode.POR_FORA)
        return Invoice(
            InvoiceReference(FiscalNumber(1), Series(1)),
            IssuanceRequestId(id.toString()),
            ClientId(CLIENTE),
            calculated,
            Instant.EPOCH,
            InvoiceRecipient(
                PersonType.FISICA,
                "***.456.789-**",
                null,
            ),
        )
    }
}
