package com.itau.desafio_diogo_bernard.model.nota

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate

data class CalculatedTax(
    val items: List<CalculatedItem>,
    val taxRate: TaxRate,
    val tax: Money,
)
