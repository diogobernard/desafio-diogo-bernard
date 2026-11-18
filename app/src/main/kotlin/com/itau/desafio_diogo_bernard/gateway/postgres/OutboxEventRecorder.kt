package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import java.time.Instant
import java.util.UUID

interface OutboxEventRecorder {
    fun prepareRequest(
        id: UUID,
        occurredAt: Instant,
    ): NewOutboxEvent

    fun recordIssuedInvoice(
        invoice: InvoiceResponse,
        occurredAt: Instant,
    )
}
