package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaxRateTableTest {
    private val table = DefaultTaxRateTable()

    @Test
    fun `aplica aliquota PF conforme a faixa nos limites`() {
        val scenarios =
            listOf(
                "499.99" to "0",
                "500.00" to "12",
                "2000.00" to "12",
                "2000.01" to "15",
                "3500.00" to "15",
                "3500.01" to "17",
            )
        scenarios.forEach { (base, percentage) ->
            val expected =
                percentage
                    .toBigDecimal()
                    .movePointLeft(2)
            assertEquals(0, expected.compareTo(taxa(PersonType.FISICA, null, base)))
        }
    }

    @Test
    fun `aplica aliquota PJ por regime e faixa nos limites`() {
        val brackets =
            mapOf(
                TaxRegime.SIMPLES_NACIONAL to listOf("3", "7", "7", "13", "13", "19"),
                TaxRegime.LUCRO_REAL to listOf("3", "9", "9", "15", "15", "20"),
                TaxRegime.LUCRO_PRESUMIDO to listOf("3", "9", "9", "16", "16", "20"),
            )
        val limits =
            listOf("999.99", "1000.00", "2000.00", "2000.01", "5000.00", "5000.01")
        brackets.forEach { (regime, taxRates) ->
            limits.zip(taxRates).forEach { (base, percentage) ->
                val expected =
                    percentage
                        .toBigDecimal()
                        .movePointLeft(2)
                assertEquals(
                    0,
                    expected.compareTo(taxa(PersonType.JURIDICA, regime, base)),
                )
            }
        }
    }

    @Test
    fun `rejeita base negativa PJ sem regime e regime OUTROS`() {
        assertFailsWith<IllegalArgumentException> {
            table.taxRate(Recipient(PersonType.FISICA), Money.of("-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            table.taxRate(Recipient(PersonType.JURIDICA), Money.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            table.taxRate(Recipient(PersonType.JURIDICA, TaxRegime.OUTROS), Money.ZERO)
        }
    }

    private fun taxa(
        type: PersonType,
        regime: TaxRegime?,
        base: String,
    ): BigDecimal = table.taxRate(Recipient(type, regime), Money.of(base)).value
}
