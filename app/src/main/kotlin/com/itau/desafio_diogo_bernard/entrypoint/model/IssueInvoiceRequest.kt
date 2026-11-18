package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.usecase.validations.OrderInput
import java.math.BigDecimal

data class IssueInvoiceRequest(
    @JsonProperty("recipient") val recipient: RecipientRequest?,
    @JsonProperty("items") val items: List<ItemRequest>?,
    @JsonProperty("freight_value") val freightValue: BigDecimal?,
    @JsonProperty("addresses") val addresses: List<AddressRequest>?,
) {
    fun toDomain(): Order {
        val freight = freightValue as BigDecimal
        return Order(
            recipient = (recipient as RecipientRequest).toDomain(),
            items = (items as List<ItemRequest>).map(ItemRequest::toDomain),
            addresses = (addresses as List<AddressRequest>).map(AddressRequest::toDomain),
            freightValue = Money.of(freight),
            providedFreightValue = freight,
        )
    }

    fun toInput(): OrderInput =
        OrderInput(
            recipient?.personType,
            recipient?.document,
            recipient?.taxRegime,
            items?.map(ItemRequest::toInput),
            freightValue,
            addresses?.map(AddressRequest::toInput),
        )
}
