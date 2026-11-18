package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.Region
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FreightTableTest {
    private val table = FreightTable()

    @Test
    fun `calcula acrescimo de frete por regiao com HALF_EVEN`() {
        val scenarios =
            listOf(
                Region.NORTE to "108.00",
                Region.NORDESTE to "108.50",
                Region.CENTRO_OESTE to "107.00",
                Region.SUDESTE to "104.80",
                Region.SUL to "106.00",
            )
        scenarios.forEach { (region, expected) ->
            assertEquals(Money.of(expected), table.calculate(Money.of("100"), region))
        }
        assertEquals(Money.of("0.04"), table.calculate(Money.of("0.75"), Region.SUL) - Money.of("0.75"))
        assertEquals(Money.of("0.02"), table.calculate(Money.of("0.25"), Region.SUL) - Money.of("0.25"))
        assertEquals(Money.of("0.08"), table.calculate(Money.of("1"), Region.NORDESTE) - Money.of("1"))
    }
}
