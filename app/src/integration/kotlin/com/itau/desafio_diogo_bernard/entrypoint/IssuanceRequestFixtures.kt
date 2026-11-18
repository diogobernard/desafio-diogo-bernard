package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.entrypoint.model.AddressRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.ItemRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.RecipientRequest
import com.itau.desafio_diogo_bernard.gateway.postgres.OutboxEventRecorder
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

internal fun novaSolicitacao(
    key: UUID = UUID.randomUUID(),
    client: String = PostgresIntegrationBase.CLIENTE,
): IssuanceRequest {
    val order =
        IssueInvoiceRequest(
            RecipientRequest(PersonType.FISICA, "52998224725", null),
            listOf(ItemRequest("SKU-1", "Produto", BigDecimal("12.50"), BigDecimal.ONE)),
            BigDecimal("2.00"),
            listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
        ).toDomain()
    val now = Instant.now()
    return IssuanceRequest(
        IssuanceRequestId(UUID.randomUUID().toString()),
        ClientId(client),
        IdempotencyKey(key.toString()),
        Fingerprint("a".repeat(64)),
        order,
        IssuanceRequestStatus.RECEBIDA,
        now,
        now,
    )
}

internal fun outboxComPayloadInvalido(topic: String): OutboxEventRecorder =
    object : OutboxEventRecorder {
        override fun prepareRequest(
            id: UUID,
            occurredAt: Instant,
        ): NewOutboxEvent = NewOutboxEvent(UUID.randomUUID(), topic, id.toString(), "{payload-invalido", null, occurredAt)

        override fun recordIssuedInvoice(
            invoice: InvoiceResponse,
            occurredAt: Instant,
        ): Unit = Unit
    }
