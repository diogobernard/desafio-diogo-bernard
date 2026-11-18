package com.itau.desafio_diogo_bernard.usecase.calculo

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.Order

class CalculateInvoice(
    private val taxRateTable: TaxRateTable = DefaultTaxRateTable(),
    private val freightTable: FreightTable = FreightTable(),
) {
    fun calculate(
        order: Order,
        mode: TaxationMode,
    ): CalculatedInvoice {
        val itemsTotal = order.items.fold(Money.ZERO) { total, item ->
            total + item.total()
        }
        val taxRate = taxRateTable.taxRate(order.recipient, itemsTotal)
        val taxation = strategy(mode).calculate(order.items, taxRate)
        val region = order.deliveryRegion()
        val freightPercentage = freightTable.percentage(region)
        val freight = freightTable.calculate(order.freightValue, region)
        return CalculatedInvoice(
            taxation.items,
            itemsTotal,
            taxation.tax,
            taxRate,
            order.freightValue,
            freightPercentage,
            freight,
            invoiceTotal(itemsTotal, taxation.tax, freight, mode),
            mode,
            region,
        )
    }

    private fun strategy(mode: TaxationMode): TaxationStrategy =
        when (mode) {
            TaxationMode.POR_FORA -> ExclusiveTaxation()
            TaxationMode.POR_DENTRO -> InclusiveTaxation()
        }

    private fun invoiceTotal(
        itemsTotal: Money,
        tax: Money,
        freight: Money,
        mode: TaxationMode,
    ): Money =
        when (mode) {
            TaxationMode.POR_FORA -> itemsTotal + tax + freight
            TaxationMode.POR_DENTRO -> itemsTotal + freight
        }
}
