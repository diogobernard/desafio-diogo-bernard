@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.exception.IngressCapacityExhaustedException
import com.itau.desafio_diogo_bernard.exception.IngressRejectionReason
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IngressCapacityExhaustedHandlerTest {
    @Test
    fun `traduz capacidade esgotada para 503 com Retry-After e conta o motivo`() {
        val registry = SimpleMeterRegistry()
        val appProperties =
            NotaFiscalProperties(consulta = NotaFiscalProperties.Consulta(retryAfter = Duration.ofSeconds(3)))
        val handler = IngressCapacityExhaustedHandler(IngressMetrics(registry), appProperties)
        val error = IngressCapacityExhaustedException(IngressRejectionReason.FILA_CHEIA)

        val response = handler.capacityExhausted(error, MockHttpServletRequest("POST", "/issuance-requests"))

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        assertEquals("3", response.headers.getFirst(HttpHeaders.RETRY_AFTER))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.body?.status)
        assertEquals("/issuance-requests", response.body?.instance)
        assertEquals(1.0, registry.counter("notafiscal.ingresso.rejeitadas", "motivo", "fila_cheia").count())
        assertEquals(0.0, registry.counter("notafiscal.ingresso.rejeitadas", "motivo", "espera_excedida").count())
        assertFalse(response.body?.detail.orEmpty().contains(error.message.orEmpty()))
    }
}
