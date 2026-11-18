package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceIssuedNotification
import com.itau.desafio_diogo_bernard.gateway.mock.SimulateConsumerDelivery
import com.itau.desafio_diogo_bernard.gateway.postgres.ReceivedNotificationPostgresGateway
import io.opentelemetry.context.Context
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class RegisterNotificationConsumer(
    private val simulator: SimulateConsumerDelivery,
    private val notifications: ReceivedNotificationPostgresGateway,
    private val mapper: ObjectMapper,
    private val correlation: ObservabilityCorrelation,
    private val metrics: BusinessMetrics,
    private val clock: Clock,
) : RegisterNotification {
    override fun receive(
        consumer: String,
        settings: Consumidor,
        payload: String,
    ): CompletableFuture<Void> = registerAsync(consumer, settings, payload)

    private fun registerAsync(
        consumer: String,
        settings: Consumidor,
        payload: String,
    ): CompletableFuture<Void> {
        val event = mapper.readValue(payload, InvoiceIssuedNotification::class.java)
        val context = Context.current()
        val startedAt = event.occurredAt
        return simulator.execute(consumer, settings) {
            correlation.executeInContext(context, "consumidor.$consumer", event.invoice.requestId) {
                val delay = Duration.between(startedAt, clock.instant())
                metrics.consumerDelay(consumer, if (delay.isNegative) Duration.ZERO else delay)
                notifications.register(consumer, event)
            }
        }
    }
}
