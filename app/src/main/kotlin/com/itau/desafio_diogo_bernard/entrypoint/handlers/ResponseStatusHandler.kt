@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ResponseStatusHandler {
    @ExceptionHandler(ResponseStatusException::class)
    fun httpStatus(
        error: ResponseStatusException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> =
        when (error.statusCode.value()) {
            HttpStatus.UNAUTHORIZED.value() -> {
                problemResponse(
                    HttpStatus.UNAUTHORIZED,
                    "Cliente não identificado",
                    "O header X-Client-Id é obrigatório ou inválido.",
                    request,
                )
            }

            HttpStatus.NOT_FOUND.value() -> {
                problemResponse(
                    HttpStatus.NOT_FOUND,
                    "Recurso não encontrado",
                    "O recurso solicitado não foi encontrado.",
                    request,
                )
            }

            else -> {
                problemResponse(
                    HttpStatus.BAD_REQUEST,
                    "Requisição inválida",
                    "Revise os headers enviados.",
                    request,
                    listOf(
                        ViolationResponse(
                            "Idempotency-Key",
                            "idempotency_key_missing",
                            "Informe um UUID válido.",
                        ),
                    ),
                )
            }
        }
}
