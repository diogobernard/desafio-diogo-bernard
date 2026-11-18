package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.entrypoint.model.AddressRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.ItemRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.RecipientRequest
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestRegistration
import com.itau.desafio_diogo_bernard.gateway.postgres.IssuanceRequestPostgresGateway
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxEventRecorder
import com.itau.desafio_diogo_bernard.gateway.postgres.lote.IssuanceRequestWrite
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Tag("integration")
class IssuanceRequestGatewayMappingIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var gateway: IssuanceRequestGateway

    @Autowired
    private lateinit var json: JsonMapper

    @Test
    fun `mapeia referencia fiscal parcial e completa e motivo de falha opcional`() {
        val partial = registrarExistente(series = null, number = 12, reason = "motivo")
        val anotherPartial = registrarExistente(series = 1, number = null)
        val complete = registrarExistente(series = 1, number = 12)

        assertEquals(null, partial.invoiceReference)
        assertEquals(null, anotherPartial.invoiceReference)
        assertEquals("motivo", partial.failureReason?.value)
        assertEquals(12, complete.invoiceReference?.number?.value)
        assertEquals(null, complete.failureReason)
    }

    @Test
    fun `falha se conflito de idempotencia nao encontrar solicitacao`() {
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val domainValue = issuanceRequest(id, key)
        val repository = Mockito.mock(IssuanceRequestRepository::class.java)
        val event = NewOutboxEvent(UUID.randomUUID(), "topico", id.toString(), "{}", null, Instant.EPOCH)
        Mockito.`when`(repository.findByClientIdAndKey(CLIENTE, key)).thenReturn(null)
        val outbox =
            object : OutboxEventRecorder {
                override fun prepareRequest(
                    id: UUID,
                    occurredAt: Instant,
                ): NewOutboxEvent = event

                override fun recordIssuedInvoice(
                    invoice: InvoiceResponse,
                    occurredAt: Instant,
                ): Unit = Unit
            }
        val adapter = IssuanceRequestPostgresGateway(repository, json, outbox, writingWithoutInsert)

        assertFailsWith<IllegalArgumentException> {
            adapter.registerOnce(domainValue)
        }
    }

    private val writingWithoutInsert =
        object : IssuanceRequestWrite {
            override fun persist(insert: IssuanceRequestInsert): Boolean = false

            override fun pending(): Int = 0
        }

    private fun registrarExistente(
        series: Int?,
        number: Int?,
        reason: String? = null,
    ): IssuanceRequest {
        val id = UUID.randomUUID()
        val key = UUID.randomUUID()
        val now = Timestamp.from(Instant.parse("2026-11-14T18:00:00Z"))
        jdbc
            .sql(
                """INSERT INTO solicitacao
            (id,cliente_id,idempotency_key,impressao_digital,pedido,status,motivo_falha,nota_serie,nota_numero,recebida_em,atualizada_em)
            VALUES (:id,:cliente,:chave,:digital,CAST(:pedido AS jsonb),'FALHA',:motivo,:serie,:numero,:agora,:agora)""",
            ).param("id", id)
            .param("cliente", CLIENTE)
            .param("chave", key)
            .param("digital", "a".repeat(64))
            .param("pedido", PEDIDO_GRAVADO)
            .param("motivo", reason)
            .param("serie", series)
            .param("numero", number)
            .param("agora", now)
            .update()
        val issuanceRequest =
            IssuanceRequest(
                IssuanceRequestId(UUID.randomUUID().toString()),
                ClientId(CLIENTE),
                IdempotencyKey(key.toString()),
                Fingerprint("a".repeat(64)),
                order(),
                IssuanceRequestStatus.RECEBIDA,
                now.toInstant(),
                now.toInstant(),
            )
        val result = gateway.registerOnce(issuanceRequest)
        return (result as IssuanceRequestRegistration.Existing).issuanceRequest
    }

    private fun order(): Order =
        IssueInvoiceRequest(
            RecipientRequest(PersonType.FISICA, "52998224725", null),
            listOf(ItemRequest("SKU-1", "Produto", BigDecimal("12.50"), BigDecimal.ONE)),
            BigDecimal("2.00"),
            listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
        ).toDomain()

    private fun issuanceRequest(
        id: UUID,
        key: UUID,
    ): IssuanceRequest =
        IssuanceRequest(
            IssuanceRequestId(id.toString()),
            ClientId(CLIENTE),
            IdempotencyKey(key.toString()),
            Fingerprint("a".repeat(64)),
            order(),
            IssuanceRequestStatus.RECEBIDA,
            Instant.EPOCH,
            Instant.EPOCH,
        )
}
