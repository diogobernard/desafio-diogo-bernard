package com.itau.desafio_diogo_bernard.model.pedido

data class Address(
    val purpose: AddressPurpose,
    val region: Region,
)

enum class AddressPurpose {
    ENTREGA,
    COBRANCA,
    COBRANCA_ENTREGA,
}

enum class Region {
    NORTE,
    NORDESTE,
    CENTRO_OESTE,
    SUDESTE,
    SUL,
}
