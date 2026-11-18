package com.itau.desafio_diogo_bernard.config

import io.micrometer.observation.ObservationRegistry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxTracingTest {
    private val tracing = OutboxTracing(ObservationRegistry.create())

    @Test
    fun `omite traceparent quando nao ha span valido`() {
        assertNull(tracing.traceparent())
    }

    @Test
    fun `formata traceparent com flags do span`() {
        val spanContext =
            SpanContext.create(
                "1234567890abcdef1234567890abcdef",
                "1234567890abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault(),
            )
        val span = Span.wrap(spanContext)

        val traceparent =
            Context
                .current()
                .with(span)
                .makeCurrent()
                .use { tracing.traceparent() }

        assertEquals("00-1234567890abcdef1234567890abcdef-1234567890abcdef-01", traceparent)
    }

    @Test
    fun `executa o registro do outbox dentro de uma observacao`() {
        var executed = false

        tracing.register("solicitacao-1") { executed = true }

        assertTrue(executed)
    }
}
