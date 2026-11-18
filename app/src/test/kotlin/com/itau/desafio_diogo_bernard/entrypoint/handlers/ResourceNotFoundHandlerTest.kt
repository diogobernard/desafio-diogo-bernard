@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceNotFoundHandlerTest {
    @Test
    fun `traduz recurso nao encontrado para 404`() {
        val response = ResourceNotFoundHandler().notFound(MockHttpServletRequest("GET", "/recursos"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("/recursos", response.body?.instance)
    }
}
