package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.ObservabilityCorrelation
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceIssuedNotification
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceIssuedReference
import com.itau.desafio_diogo_bernard.gateway.mock.SimulateConsumerDelivery
import com.itau.desafio_diogo_bernard.gateway.postgres.ReceivedNotificationPostgresGateway
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class RegisterNotificationConsumerTest {
    @Test
    fun `normaliza atraso negativo para zero`() {
        val occurredAt = Instant.parse("2026-11-14T18:00:00Z")
        val event = event(occurredAt)
        val mapper = Mockito.mock(ObjectMapper::class.java)
        Mockito.`when`(mapper.readValue("{}", InvoiceIssuedNotification::class.java)).thenReturn(event)
        val repository = Mockito.mock(ReceivedNotificationPostgresGateway::class.java)
        val registry = SimpleMeterRegistry()
        val simulator =
            object : SimulateConsumerDelivery {
                override fun execute(
                    consumer: String,
                    settings: Consumidor,
                    action: Runnable,
                ): CompletableFuture<Void> {
                    action.run()
                    return CompletableFuture.completedFuture(null)
                }
            }
        val registerNotification =
            RegisterNotificationConsumer(
                simulator,
                repository,
                mapper,
                ObservabilityCorrelation(ObservationRegistry.create()),
                BusinessMetrics(registry),
                Clock.fixed(occurredAt.minusSeconds(1), ZoneOffset.UTC),
            )

        registerNotification.receive("estoque", Consumidor(), "{}").get()

        Mockito.verify(repository).register("estoque", event)
        assertEquals(
            0.0,
            registry
                .get("notafiscal.consumidor.atraso")
                .tag("consumidor", "estoque")
                .timer()
                .max(TimeUnit.NANOSECONDS),
        )
    }

    private fun event(occurredAt: Instant): InvoiceIssuedNotification =
        InvoiceIssuedNotification(
            "01933b6e-7c1a-7001-9f10-2b6a4c8d0e11",
            occurredAt,
            InvoiceIssuedReference(1, 1, "solicitacao"),
        )
}
