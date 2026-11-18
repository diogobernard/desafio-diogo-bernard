@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class InvalidJsonHandlerTest {
    @Test
    fun `traduz json invalido para 400 com o erro do corpo`() {
        val response = InvalidJsonHandler().invalidJson(MockHttpServletRequest("POST", "/issuance-requests"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("json_malformed", response.body?.errors?.single()?.code)
    }
}
