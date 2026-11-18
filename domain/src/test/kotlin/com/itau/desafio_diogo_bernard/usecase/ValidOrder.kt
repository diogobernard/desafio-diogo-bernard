package com.itau.desafio_diogo_bernard.usecase

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.Address
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.Document
import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.Region

internal fun pedidoValido(document: String = "12345678909"): Order =
    Order(
        recipient = Recipient(PersonType.FISICA, document = Document(document)),
        items = listOf(OrderItem("SKU", "Produto", Money.of("50.00"), 2)),
        addresses = listOf(Address(AddressPurpose.ENTREGA, Region.SUL)),
        freightValue = Money.of("5.00"),
    )
