@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.itau.desafio_diogo_bernard.exception.IdempotencyKeyReusedException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class IdempotencyKeyReusedHandler {
    @ExceptionHandler(IdempotencyKeyReusedException::class)
    fun reusedKey(request: HttpServletRequest): ResponseEntity<ProblemDetails> =
        problemResponse(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Idempotency-Key já utilizada",
            "A chave foi usada com outro pedido.",
            request,
            listOf(
                ViolationResponse(
                    "Idempotency-Key",
                    "idempotency_key_reused",
                    "A chave já foi usada com outro conteúdo.",
                ),
            ),
        )
}
