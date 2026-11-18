package com.itau.desafio_diogo_bernard.repository.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.pedido.Order
import java.math.BigDecimal

/**
 * Formato do pedido gravado na coluna `pedido` (jsonb). É independente do contrato HTTP:
 * renomear campos da API não pode impedir a leitura de solicitações já gravadas.
 */
data class StoredOrder(
    @JsonProperty("destinatario") val recipient: StoredRecipient,
    @JsonProperty("itens") val items: List<StoredOrderItem>,
    @JsonProperty("valor_frete") val freightValue: BigDecimal,
    @JsonProperty("enderecos") val addresses: List<StoredAddress>,
) {
    fun toDomain(): Order =
        Order(
            recipient = recipient.toDomain(),
            items = items.map(StoredOrderItem::toDomain),
            addresses = addresses.map(StoredAddress::toDomain),
            freightValue = Money.of(freightValue),
            providedFreightValue = freightValue,
        )

    companion object {
        fun fromDomain(order: Order): StoredOrder =
            StoredOrder(
                StoredRecipient.fromDomain(order.recipient),
                order.items.map(StoredOrderItem::fromDomain),
                order.providedFreightValue,
                order.addresses.map(StoredAddress::fromDomain),
            )
    }
}
