@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.itau.desafio_diogo_bernard.config.IngressMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.exception.IngressCapacityExhaustedException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IngressCapacityExhaustedHandler(
    private val ingressMetrics: IngressMetrics,
    private val properties: NotaFiscalProperties,
) {
    @ExceptionHandler(IngressCapacityExhaustedException::class)
    fun capacityExhausted(
        error: IngressCapacityExhaustedException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        ingressMetrics.rejected(error.reason)
        val problem =
            problemResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Capacidade esgotada",
                "A API está sem capacidade no momento. Repita a requisição com a mesma Idempotency-Key.",
                request,
            )
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(HttpHeaders.RETRY_AFTER, properties.consulta.retryAfter.seconds.toString())
            .body(problem.body)
    }
}
