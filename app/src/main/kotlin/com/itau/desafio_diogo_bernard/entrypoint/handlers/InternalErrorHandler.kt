@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// O Spring consulta os advices em ordem e usa o primeiro que casa com a exceção. Este captura
// qualquer Exception, então precisa ser o último para não esconder os handlers específicos.
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class InternalErrorHandler {
    @ExceptionHandler(Exception::class)
    fun internalError(request: HttpServletRequest): ResponseEntity<ProblemDetails> =
        problemResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno",
            "Não foi possível processar a solicitação.",
            request,
        )
}
