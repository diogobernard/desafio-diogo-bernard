package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import java.math.BigDecimal

data class OrderInput(
    val personType: PersonType?,
    val document: String?,
    val taxRegime: TaxRegime?,
    val items: List<OrderItemInput>?,
    val freightValue: BigDecimal?,
    val addresses: List<AddressInput>?,
)

data class OrderItemInput(
    val code: String?,
    val description: String?,
    val unitValue: BigDecimal?,
    val quantity: BigDecimal?,
)

data class AddressInput(
    val purpose: AddressPurpose?,
    val region: Region?,
)
