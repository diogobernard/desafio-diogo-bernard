@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.itau.desafio_diogo_bernard.exception.InvalidOrderException
import com.itau.desafio_diogo_bernard.usecase.validations.ViolationType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class InvalidOrderHandler {
    @ExceptionHandler(InvalidOrderException::class)
    fun invalidOrder(
        error: InvalidOrderException,
        request: HttpServletRequest,
    ): ResponseEntity<ProblemDetails> {
        val status =
            if (error.violations.all { it.type == ViolationType.IMPEDITIVO }) {
                HttpStatus.UNPROCESSABLE_ENTITY
            } else {
                HttpStatus.BAD_REQUEST
            }
        return problemResponse(
            status,
            if (status == HttpStatus.BAD_REQUEST) "Requisição inválida" else "Pedido não pode ser emitido",
            "O pedido contém ${error.violations.size} erro(s).",
            request,
            error.violations.map(ViolationResponse::fromDomain),
        )
    }
}
