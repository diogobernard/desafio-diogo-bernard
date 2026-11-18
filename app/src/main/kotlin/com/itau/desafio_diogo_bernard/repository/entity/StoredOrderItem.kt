package com.itau.desafio_diogo_bernard.repository.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import java.math.BigDecimal

data class StoredOrderItem(
    @JsonProperty("codigo") val code: String,
    @JsonProperty("descricao") val description: String,
    @JsonProperty("valor_unitario") val unitValue: BigDecimal,
    @JsonProperty("quantidade") val quantity: Int,
) {
    fun toDomain(): OrderItem = OrderItem(code, description, Money.of(unitValue), quantity, unitValue)

    companion object {
        fun fromDomain(item: OrderItem): StoredOrderItem =
            StoredOrderItem(item.code, item.description, item.providedUnitValue, item.quantity)
    }
}
