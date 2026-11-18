package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.pedido.Address
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.usecase.validations.AddressInput

data class AddressRequest(
    @JsonProperty("purpose") val purpose: AddressPurpose?,
    @JsonProperty("region") val region: Region?,
    @JsonProperty("street") val street: String? = null,
    @JsonProperty("city") val city: String? = null,
    @JsonProperty("state") val uf: String? = null,
    @JsonProperty("postal_code") val zipCode: String? = null,
) {
    fun toDomain(): Address = Address(requireNotNull(purpose), requireNotNull(region))

    fun toInput(): AddressInput = AddressInput(purpose, region)
}
