package com.itau.desafio_diogo_bernard.model.nota

import com.itau.desafio_diogo_bernard.model.dinheiro.Money

data class CalculatedItem(
    val code: String,
    val description: String,
    val unitValue: Money,
    val quantity: Int,
    val itemTotal: Money,
    val itemTax: Money,
    val netValue: Money?,
)
