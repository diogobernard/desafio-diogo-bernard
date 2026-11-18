@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import io.opentelemetry.api.trace.Span
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.util.UUID

internal fun problemResponse(
    status: HttpStatus,
    titleText: String,
    detail: String,
    request: HttpServletRequest,
    errors: List<ViolationResponse> = emptyList(),
): ResponseEntity<ProblemDetails> =
    ResponseEntity
        .status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(
            ProblemDetails(
                "https://api.notafiscal.local/problemas/${status.value()}",
                titleText,
                status.value(),
                detail,
                request.requestURI,
                traceId(),
                errors,
            ),
        )

private fun traceId(): String {
    val context = Span.current().spanContext
    return if (context.isValid) context.traceId else UUID.randomUUID().toString().replace("-", "")
}
