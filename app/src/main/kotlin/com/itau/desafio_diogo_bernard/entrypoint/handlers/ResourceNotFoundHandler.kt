@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.itau.desafio_diogo_bernard.exception.InvoiceNotFoundException
import com.itau.desafio_diogo_bernard.exception.IssuanceRequestNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ResourceNotFoundHandler {
    @ExceptionHandler(
        IssuanceRequestNotFoundException::class,
        InvoiceNotFoundException::class,
        NoResourceFoundException::class,
    )
    fun notFound(request: HttpServletRequest): ResponseEntity<ProblemDetails> =
        problemResponse(
            HttpStatus.NOT_FOUND,
            "Recurso não encontrado",
            "O recurso solicitado não foi encontrado.",
            request,
        )
}
