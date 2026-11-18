package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.nota.CalculatedTax
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem

interface TaxationStrategy {
    fun calculate(
        items: List<OrderItem>,
        taxRate: TaxRate,
    ): CalculatedTax
}
