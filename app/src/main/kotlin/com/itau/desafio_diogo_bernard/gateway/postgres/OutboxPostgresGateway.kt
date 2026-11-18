package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.OutboxTracing
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceEventPayload
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceIssuedEvent
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestIdGenerator
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import com.itau.desafio_diogo_bernard.repository.OutboxRepository as Eventos

@Component
class OutboxPostgresGateway(
    private val events: Eventos,
    private val idGenerator: IssuanceRequestIdGenerator,
    private val json: JsonMapper,
    private val properties: NotaFiscalProperties,
    private val tracing: OutboxTracing,
) : OutboxEventRecorder {
    override fun prepareRequest(
        id: UUID,
        occurredAt: Instant,
    ): NewOutboxEvent {
        val eventId = UUID.fromString(idGenerator.generate().value)
        val body =
            mapOf(
                "evento_id" to eventId,
                "versao" to VERSAO_EVENTO,
                "ocorrido_em" to occurredAt,
                "solicitacao_id" to id.toString(),
            )
        return NewOutboxEvent(
            eventId,
            properties.kafka.topicoSolicitacoes,
            id.toString(),
            json.writeValueAsString(body),
            tracing.traceparent(),
            occurredAt,
        )
    }

    override fun recordIssuedInvoice(
        invoice: InvoiceResponse,
        occurredAt: Instant,
    ) {
        val eventId = idGenerator.generate().value
        val event = InvoiceIssuedEvent(eventId, VERSAO_EVENTO, occurredAt, InvoiceEventPayload.from(invoice))
        tracing.register(invoice.requestId) {
            val newItem =
                NewOutboxEvent(
                    UUID.fromString(eventId),
                    properties.kafka.topicoNotas,
                    invoice.requestId,
                    json.writeValueAsString(event),
                    tracing.traceparent(),
                    occurredAt,
                )
            events.save(newItem.toEntity())
        }
    }

    private companion object {
        const val VERSAO_EVENTO = 1
    }
}
