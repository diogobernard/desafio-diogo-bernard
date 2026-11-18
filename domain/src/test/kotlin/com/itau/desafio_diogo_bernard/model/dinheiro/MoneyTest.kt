package com.itau.desafio_diogo_bernard.model.dinheiro

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun `normaliza valores monetarios com arredondamento HALF_EVEN`() {
        assertEquals(Money.of("100.00"), Money.of("100.005"))
        assertEquals(Money.of("100.02"), Money.of("100.015"))
        assertEquals(Money.of("0.12"), Money.of("0.125"))
    }

    @Test
    fun `opera com valores normalizados`() {
        val twoReais = Money.of("2.00")
        assertEquals(Money.of("3.00"), twoReais + Money.of("1"))
        assertEquals(Money.of("1.00"), twoReais - Money.of("1"))
        assertEquals(Money.of("6.00"), twoReais * 3)
        assertEquals(Money.of("1.00"), twoReais.dividedBy(Money.of("2")))
        assertEquals(0, twoReais.compareTo(Money.of("2.000")))
    }

    @Test
    fun `reconstroi aliquota a partir da fracao gravada sem zeros de escala`() {
        val taxRate = TaxRate.fraction(BigDecimal("0.0480"))

        assertEquals(TaxRate.percentage("4.8"), taxRate)
        assertEquals("0.048", taxRate.value.toPlainString())
    }

    @Test
    fun `rejeita fracao de aliquota fora do intervalo`() {
        assertFailsWith<IllegalArgumentException> { TaxRate.fraction(BigDecimal("-0.0001")) }
        assertFailsWith<IllegalArgumentException> { TaxRate.fraction(BigDecimal("1.0001")) }
    }

    @Test
    fun `rejeita aliquota fora do intervalo percentual`() {
        assertFailsWith<IllegalArgumentException> { TaxRate.percentage("-1") }
        assertFailsWith<IllegalArgumentException> { TaxRate.percentage("100.01") }
    }

    @Test
    fun `expõe os BigDecimal de Dinheiro e Aliquota pela API JVM`() {
        val money: Any = Money.of("2.50")
        val taxRate: Any = TaxRate.percentage("12")

        val moneyValue = money.javaClass.getMethod("getValue").invoke(money)
        val taxRateValue = taxRate.javaClass.getMethod("getValue").invoke(taxRate)

        assertEquals(BigDecimal("2.50"), moneyValue)
        assertEquals(BigDecimal("0.12"), taxRateValue)
    }
}
