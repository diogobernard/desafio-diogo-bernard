package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.nota.CalculatedItem
import com.itau.desafio_diogo_bernard.model.nota.CalculatedTax
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem

class ExclusiveTaxation : TaxationStrategy {
    override fun calculate(
        items: List<OrderItem>,
        taxRate: TaxRate,
    ): CalculatedTax {
        val calculatedItems = items.map { it.calculateExclusiveTax(taxRate) }
        val totalTax = calculatedItems.fold(Money.ZERO) { total, item -> total + item.itemTax }
        return CalculatedTax(calculatedItems, taxRate, totalTax)
    }

    private fun OrderItem.calculateExclusiveTax(taxRate: TaxRate): CalculatedItem {
        val itemTotal = total()
        return CalculatedItem(code, description, unitValue, quantity, itemTotal, itemTotal * taxRate, null)
    }
}
