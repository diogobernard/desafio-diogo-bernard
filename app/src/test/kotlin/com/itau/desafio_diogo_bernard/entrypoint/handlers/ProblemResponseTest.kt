@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProblemResponseTest {
    private val request = MockHttpServletRequest("GET", "/recursos")

    @Test
    fun `propaga trace id valido para problem details`() {
        val traceId = "1234567890abcdef1234567890abcdef"
        val span =
            Span.wrap(
                SpanContext.create(traceId, "1234567890abcdef", TraceFlags.getSampled(), TraceState.getDefault()),
            )

        val response =
            Context
                .current()
                .with(span)
                .makeCurrent()
                .use { problemResponse(HttpStatus.BAD_REQUEST, "Titulo", "Detalhe", request) }

        assertEquals(traceId, response.body?.traceId)
    }

    @Test
    fun `gera trace id quando o span atual e invalido`() {
        val response =
            Context
                .root()
                .with(Span.getInvalid())
                .makeCurrent()
                .use { problemResponse(HttpStatus.BAD_REQUEST, "Titulo", "Detalhe", request) }

        assertEquals(32, response.body?.traceId?.length)
    }
}
