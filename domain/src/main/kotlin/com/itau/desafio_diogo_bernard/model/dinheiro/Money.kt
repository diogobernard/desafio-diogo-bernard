package com.itau.desafio_diogo_bernard.model.dinheiro

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money private constructor(
    val value: BigDecimal,
) : Comparable<Money> {
    operator fun plus(another: Money): Money = of(value + another.value)

    operator fun minus(another: Money): Money = of(value - another.value)

    operator fun times(multiplier: Int): Money = of(value * multiplier.toBigDecimal())

    operator fun times(taxRate: TaxRate): Money = of(value * taxRate.value)

    fun dividedBy(divisor: Money): Money = of(value.divide(divisor.value, ESCALA_MONETARIA, ARREDONDAMENTO))

    override fun compareTo(other: Money): Int = value.compareTo(other.value)

    companion object {
        const val ESCALA_MONETARIA = 2
        private val ARREDONDAMENTO = RoundingMode.HALF_EVEN
        val ZERO: Money = of(BigDecimal.ZERO)
        val UM: Money = of(BigDecimal.ONE)

        fun of(value: BigDecimal): Money = Money(value.setScale(ESCALA_MONETARIA, ARREDONDAMENTO))

        fun of(value: String): Money = of(value.toBigDecimal())
    }
}
