@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseStatusHandlerTest {
    private val handler = ResponseStatusHandler()
    private val request = MockHttpServletRequest("GET", "/recursos")

    @Test
    fun `mantem 401 quando o status e nao autorizado`() {
        val response = handler.httpStatus(ResponseStatusException(HttpStatus.UNAUTHORIZED), request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `mantem 404 quando o status e nao encontrado`() {
        val response = handler.httpStatus(ResponseStatusException(HttpStatus.NOT_FOUND), request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `traduz os demais status para 400 com o erro do header`() {
        val response = handler.httpStatus(ResponseStatusException(HttpStatus.BAD_REQUEST), request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("idempotency_key_missing", response.body?.errors?.single()?.code)
    }
}
