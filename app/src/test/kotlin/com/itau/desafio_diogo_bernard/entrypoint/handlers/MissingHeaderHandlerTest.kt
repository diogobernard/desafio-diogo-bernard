@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.MissingRequestHeaderException
import kotlin.test.Test
import kotlin.test.assertEquals

class MissingHeaderHandlerTest {
    private val handler = MissingHeaderHandler()
    private val request = MockHttpServletRequest("POST", "/issuance-requests")
    private val parameter = MethodParameter(javaClass.getDeclaredMethod("header", String::class.java), 0)

    @Test
    fun `traduz cabecalho de cliente ausente para 401`() {
        val response = handler.missingHeader(MissingRequestHeaderException("X-Client-Id", parameter), request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `traduz cabecalho de chave ausente para 400 com o erro do header`() {
        val response = handler.missingHeader(MissingRequestHeaderException("Idempotency-Key", parameter), request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("idempotency_key_missing", response.body?.errors?.single()?.code)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun header(value: String): String = value
}
