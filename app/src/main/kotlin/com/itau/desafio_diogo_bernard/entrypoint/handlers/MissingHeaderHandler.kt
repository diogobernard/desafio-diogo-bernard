@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private const val HEADER_CLIENT = "X-Client-Id"

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class MissingHeaderHandler {
    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(
        error: MissingRequestHeaderException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> =
        if (error.headerName == HEADER_CLIENT) {
            problemResponse(
                HttpStatus.UNAUTHORIZED,
                "Cliente não identificado",
                "O header X-Client-Id é obrigatório.",
                request,
            )
        } else {
            problemResponse(
                HttpStatus.BAD_REQUEST,
                "Requisição inválida",
                "O header Idempotency-Key é obrigatório.",
                request,
                listOf(ViolationResponse("Idempotency-Key", "idempotency_key_missing", "Informe um UUID válido.")),
            )
        }
}
