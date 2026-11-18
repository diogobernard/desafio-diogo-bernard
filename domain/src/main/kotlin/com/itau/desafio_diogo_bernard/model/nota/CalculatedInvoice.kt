package com.itau.desafio_diogo_bernard.model.nota

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.pedido.Region

data class CalculatedInvoice(
    val items: List<CalculatedItem>,
    val itemsTotal: Money,
    val tax: Money,
    val taxRate: TaxRate,
    val freightValue: Money,
    val freightPercentage: TaxRate,
    val freight: Money,
    val invoiceTotal: Money,
    val mode: TaxationMode,
    val deliveryRegion: Region,
)
