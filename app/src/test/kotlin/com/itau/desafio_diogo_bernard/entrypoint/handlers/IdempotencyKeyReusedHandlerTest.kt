@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class IdempotencyKeyReusedHandlerTest {
    @Test
    fun `traduz chave reutilizada para 422 com o erro do header`() {
        val request = MockHttpServletRequest("POST", "/issuance-requests")

        val response = IdempotencyKeyReusedHandler().reusedKey(request)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("idempotency_key_reused", response.body?.errors?.single()?.code)
    }
}
