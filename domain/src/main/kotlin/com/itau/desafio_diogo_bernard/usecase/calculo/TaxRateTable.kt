package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.pedido.Recipient

interface TaxRateTable {
    fun taxRate(
        recipient: Recipient,
        base: Money,
    ): TaxRate
}
