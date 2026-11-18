package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.pedido.Region

class FreightTable {
    private val percentages =
        mapOf(
            Region.NORTE to TaxRate.percentage("8"),
            Region.NORDESTE to TaxRate.percentage("8.5"),
            Region.CENTRO_OESTE to TaxRate.percentage("7"),
            Region.SUDESTE to TaxRate.percentage("4.8"),
            Region.SUL to TaxRate.percentage("6"),
        )

    fun percentage(region: Region): TaxRate = percentages.getValue(region)

    fun calculate(
        freightValue: Money,
        region: Region,
    ): Money = freightValue + (freightValue * percentage(region))
}
