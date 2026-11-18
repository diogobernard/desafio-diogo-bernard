@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.itau.desafio_diogo_bernard.exception.InvalidOrderException
import com.itau.desafio_diogo_bernard.usecase.validations.Violation
import com.itau.desafio_diogo_bernard.usecase.validations.ViolationType
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class InvalidOrderHandlerTest {
    private val handler = InvalidOrderHandler()
    private val request = MockHttpServletRequest("POST", "/issuance-requests")

    @Test
    fun `traduz excecoes para problem details`() {
        val error =
            InvalidOrderException(
                listOf(Violation("itens[0].quantidade", ViolationType.MALFORMADO, "Valor inválido")),
            )

        val response = handler.invalidOrder(error, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("invalid_value", response.body?.errors?.single()?.code)
        assertEquals("/issuance-requests", response.body?.instance)
    }

    @Test
    fun `retorna 422 quando todas as violacoes impedem a emissao`() {
        val error =
            InvalidOrderException(
                listOf(Violation("destinatario.regime_tributacao", ViolationType.IMPEDITIVO, "Regime não suportado")),
            )

        val response = handler.invalidOrder(error, request)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertEquals("unsupported_tax_regime", response.body?.errors?.single()?.code)
    }

    @Test
    fun `traduz violacoes de cada categoria e os campos conhecidos`() {
        val error =
            InvalidOrderException(
                listOf(
                    Violation("campo", ViolationType.AUSENTE, "ausente"),
                    Violation("itens", ViolationType.MALFORMADO, "sem itens"),
                    Violation("enderecos", ViolationType.MALFORMADO, "sem entrega"),
                    Violation("valor", ViolationType.MALFORMADO, "invalido"),
                    Violation("destinatario.regime_tributacao", ViolationType.IMPEDITIVO, "regime"),
                    Violation("itens", ViolationType.IMPEDITIVO, "limite"),
                ),
            )

        val response = handler.invalidOrder(error, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(
            listOf(
                "required_field",
                "empty_items",
                "missing_delivery_address",
                "invalid_value",
                "unsupported_tax_regime",
                "item_limit_exceeded",
            ),
            response.body?.errors?.map { it.code },
        )
    }
}
