package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.nota.CalculatedItem
import com.itau.desafio_diogo_bernard.model.nota.CalculatedTax
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import java.math.RoundingMode

class InclusiveTaxation : TaxationStrategy {
    override fun calculate(
        items: List<OrderItem>,
        taxRate: TaxRate,
    ): CalculatedTax {
        val calculatedItems = items.map { it.calculateInclusiveTax(taxRate) }
        val totalTax = calculatedItems.fold(Money.ZERO) { total, item -> total + item.itemTax }
        return CalculatedTax(calculatedItems, taxRate, totalTax)
    }

    private fun OrderItem.calculateInclusiveTax(taxRate: TaxRate): CalculatedItem {
        val grossTotal = total()
        val factor = Money.UM.value + taxRate.value
        val tax =
            Money.of(
                grossTotal.value
                    .multiply(taxRate.value)
                    .divide(factor, Money.ESCALA_MONETARIA, RoundingMode.HALF_EVEN),
            )
        return CalculatedItem(
            code,
            description,
            unitValue,
            quantity,
            grossTotal,
            tax,
            grossTotal - tax,
        )
    }
}
