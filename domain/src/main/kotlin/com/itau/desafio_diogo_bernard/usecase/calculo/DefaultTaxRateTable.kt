package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime

class DefaultTaxRateTable : TaxRateTable {
    private val individualExemptionLimit = Money.of("499.99")
    private val individualSecondBracketLimit = Money.of("2000.00")
    private val individualThirdBracketLimit = Money.of("3500.00")
    private val legalEntityInitialLimit = Money.of("999.99")
    private val legalEntitySecondBracketLimit = Money.of("2000.00")
    private val legalEntityThirdBracketLimit = Money.of("5000.00")
    private val individualTaxRates = listOf(
        taxRate("0"),
        taxRate("12"),
        taxRate("15"),
        taxRate("17"),
    )
    private val legalEntityTaxRates =
        mapOf(
            TaxRegime.SIMPLES_NACIONAL to
                listOf(
                    taxRate("3"),
                    taxRate("7"),
                    taxRate("13"),
                    taxRate("19"),
                ),
            TaxRegime.LUCRO_REAL to
                listOf(
                    taxRate("3"),
                    taxRate("9"),
                    taxRate("15"),
                    taxRate("20"),
                ),
            TaxRegime.LUCRO_PRESUMIDO to
                listOf(
                    taxRate("3"),
                    taxRate("9"),
                    taxRate("16"),
                    taxRate("20"),
                ),
        )

    override fun taxRate(
        recipient: Recipient,
        base: Money,
    ): TaxRate {
        require(base >= Money.ZERO) { "A base de cálculo não pode ser negativa" }
        return when (recipient.personType) {
            PersonType.FISICA -> individualTaxRates[individualBracket(base)]
            PersonType.JURIDICA -> legalEntityTaxRate(recipient.taxRegime, legalEntityBracket(base))
        }
    }

    private fun individualBracket(base: Money): Int =
        when {
            base <= individualExemptionLimit -> 0
            base <= individualSecondBracketLimit -> 1
            base <= individualThirdBracketLimit -> 2
            else -> 3
        }

    private fun legalEntityBracket(base: Money): Int =
        when {
            base <= legalEntityInitialLimit -> 0
            base <= legalEntitySecondBracketLimit -> 1
            base <= legalEntityThirdBracketLimit -> 2
            else -> 3
        }

    private fun legalEntityTaxRate(
        regime: TaxRegime?,
        bracket: Int,
    ): TaxRate {
        requireNotNull(regime) { "Pessoa jurídica deve possuir regime de tributação" }
        val taxRates = legalEntityTaxRates[regime]
        requireNotNull(taxRates) { "O regime de tributação OUTROS não é suportado" }
        return taxRates[bracket]
    }

    private fun taxRate(percentage: String): TaxRate = TaxRate.percentage(percentage)
}
