package com.itau.desafio_diogo_bernard.model.pedido

data class Recipient(
    val personType: PersonType,
    val taxRegime: TaxRegime? = null,
    val document: Document? = null,
) {
    init {
        require(personType == PersonType.JURIDICA || taxRegime == null) {
            "Pessoa física não possui regime de tributação"
        }
    }
}
