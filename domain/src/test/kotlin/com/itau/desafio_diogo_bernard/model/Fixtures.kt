package com.itau.desafio_diogo_bernard.model

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.Address
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime

internal fun item(
    code: String = "SKU-001",
    price: String = "100.00",
    quantity: Int = 1,
): OrderItem = OrderItem(code, "Produto $code", Money.of(price), quantity)

internal fun order(
    items: List<OrderItem> = listOf(item()),
    person: PersonType = PersonType.FISICA,
    regime: TaxRegime? = null,
    freightValue: String = "50.00",
    region: Region = Region.SUDESTE,
): Order =
    Order(
        Recipient(person, regime),
        items,
        listOf(Address(AddressPurpose.ENTREGA, region)),
        Money.of(freightValue),
    )
