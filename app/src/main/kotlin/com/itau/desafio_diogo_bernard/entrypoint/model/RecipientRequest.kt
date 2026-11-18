package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.pedido.Document
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime

data class RecipientRequest(
    @JsonProperty("person_type") val personType: PersonType?,
    @JsonProperty("document") val document: String?,
    @JsonProperty("tax_regime") val taxRegime: TaxRegime?,
) {
    fun toDomain(): Recipient =
        Recipient(
            requireNotNull(personType),
            taxRegime,
            document?.let(::Document),
        )
}
