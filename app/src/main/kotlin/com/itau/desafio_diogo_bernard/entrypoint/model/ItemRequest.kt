package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.OrderItem
import com.itau.desafio_diogo_bernard.usecase.validations.OrderItemInput
import java.math.BigDecimal

data class ItemRequest(
    @JsonProperty("code") val code: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("unit_price") val unitValue: BigDecimal?,
    @JsonProperty("quantity") val quantity: BigDecimal?,
) {
    fun toDomain(): OrderItem {
        val value = requireNotNull(unitValue)
        return OrderItem(
            requireNotNull(code),
            requireNotNull(description),
            Money.of(value),
            requireNotNull(quantity).intValueExact(),
            value,
        )
    }

    fun toInput(): OrderItemInput = OrderItemInput(code, description, unitValue, quantity)
}
