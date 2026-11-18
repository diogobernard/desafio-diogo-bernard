@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class InternalErrorHandlerTest {
    @Test
    fun `traduz falha inesperada para 500 sem expor a causa`() {
        val response = InternalErrorHandler().internalError(MockHttpServletRequest("GET", "/recursos"))

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Não foi possível processar a solicitação.", response.body?.detail)
    }
}
