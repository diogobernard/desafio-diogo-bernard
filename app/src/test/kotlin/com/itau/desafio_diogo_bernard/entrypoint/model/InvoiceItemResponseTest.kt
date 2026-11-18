package com.itau.desafio_diogo_bernard.entrypoint.model

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.nota.CalculatedItem
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvoiceItemResponseTest {
    @Test
    fun `converte item com tributo por dentro e preserva valor liquido`() {
        val item = itemCalculado(netValue = Money.of("18.00"))

        val response = InvoiceItemResponse.fromDomain(item)

        assertEquals(
            InvoiceItemResponse(
                "SKU",
                "Produto",
                BigDecimal("10.00"),
                2,
                BigDecimal("20.00"),
                BigDecimal("2.00"),
                BigDecimal("18.00"),
            ),
            response,
        )
    }

    @Test
    fun `converte item por fora sem valor liquido`() {
        val response = InvoiceItemResponse.fromDomain(itemCalculado(netValue = null))

        assertNull(response.netValue)
    }

    private fun itemCalculado(netValue: Money?): CalculatedItem =
        CalculatedItem(
            "SKU",
            "Produto",
            Money.of("10.00"),
            2,
            Money.of("20.00"),
            Money.of("2.00"),
            netValue,
        )
}
