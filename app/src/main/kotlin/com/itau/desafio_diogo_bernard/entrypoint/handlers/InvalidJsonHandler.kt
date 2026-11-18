@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class InvalidJsonHandler {
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun invalidJson(request: HttpServletRequest): ResponseEntity<ProblemDetails> =
        problemResponse(
            HttpStatus.BAD_REQUEST,
            "Requisição inválida",
            "O corpo JSON está malformado ou contém um valor inválido.",
            request,
            listOf(ViolationResponse("body", "json_malformed", "Revise o formato e os tipos dos campos.")),
        )
}
