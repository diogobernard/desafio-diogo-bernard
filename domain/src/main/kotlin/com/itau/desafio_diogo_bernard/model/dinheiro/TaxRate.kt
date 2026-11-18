package com.itau.desafio_diogo_bernard.model.dinheiro

import java.math.BigDecimal

@JvmInline
value class TaxRate private constructor(
    val value: BigDecimal,
) {
    companion object {
        fun percentage(value: String): TaxRate {
            val percentage = value.toBigDecimal()
            require(percentage >= BigDecimal.ZERO && percentage <= CEM) {
                "A alíquota deve estar entre 0 e 100 por cento"
            }
            return TaxRate(percentage.divide(CEM))
        }

        /** Reconstrói a alíquota a partir da fração já calculada (0 a 1), sem os zeros de escala da coluna. */
        fun fraction(value: BigDecimal): TaxRate {
            require(value >= BigDecimal.ZERO && value <= BigDecimal.ONE) {
                "A alíquota deve estar entre 0 e 100 por cento"
            }
            return TaxRate(value.stripTrailingZeros())
        }

        private val CEM = BigDecimal("100")
    }
}
