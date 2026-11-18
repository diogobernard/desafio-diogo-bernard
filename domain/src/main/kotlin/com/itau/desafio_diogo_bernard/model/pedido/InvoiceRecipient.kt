package com.itau.desafio_diogo_bernard.model.pedido

data class InvoiceRecipient(
    val personType: PersonType,
    val maskedDocument: String,
    val taxRegime: TaxRegime?,
)
