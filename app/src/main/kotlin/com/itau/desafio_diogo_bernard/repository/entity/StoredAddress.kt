package com.itau.desafio_diogo_bernard.repository.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.pedido.Address
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.Region

data class StoredAddress(
    @JsonProperty("finalidade") val purpose: AddressPurpose,
    @JsonProperty("regiao") val region: Region,
) {
    fun toDomain(): Address = Address(purpose, region)

    companion object {
        fun fromDomain(address: Address): StoredAddress = StoredAddress(address.purpose, address.region)
    }
}
