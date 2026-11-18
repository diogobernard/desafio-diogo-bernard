package com.itau.desafio_diogo_bernard.model.pedido

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import java.math.BigDecimal

data class Order(
    val recipient: Recipient,
    val items: List<OrderItem>,
    val addresses: List<Address>,
    val freightValue: Money,
    val providedFreightValue: BigDecimal = freightValue.value,
) {
    init {
        require(items.isNotEmpty()) { "O pedido deve conter ao menos um item" }
        require(freightValue >= Money.ZERO) { "O valor do frete não pode ser negativo" }
        require(providedFreightValue >= BigDecimal.ZERO) { "O valor do frete informado não pode ser negativo" }
        require(addresses.count(Address::isDelivery) == 1) {
            "O pedido deve conter exatamente um endereço de entrega"
        }
    }

    fun deliveryRegion(): Region = addresses.single(Address::isDelivery).region
}

private fun Address.isDelivery(): Boolean =
    when (purpose) {
        AddressPurpose.ENTREGA, AddressPurpose.COBRANCA_ENTREGA -> true
        AddressPurpose.COBRANCA -> false
    }
