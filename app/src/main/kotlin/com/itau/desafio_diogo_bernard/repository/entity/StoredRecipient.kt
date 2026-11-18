package com.itau.desafio_diogo_bernard.repository.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.pedido.Document
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime

data class StoredRecipient(
    @JsonProperty("tipo_pessoa") val personType: PersonType,
    @JsonProperty("documento") val document: String,
    @JsonProperty("regime_tributacao") val taxRegime: TaxRegime?,
) {
    fun toDomain(): Recipient = Recipient(personType, taxRegime, Document(document))

    companion object {
        private const val DOCUMENTO_OBRIGATORIO = "O documento do destinatário é obrigatório para gravar o pedido"

        fun fromDomain(recipient: Recipient): StoredRecipient {
            val document = requireNotNull(recipient.document) { DOCUMENTO_OBRIGATORIO }
            return StoredRecipient(recipient.personType, document.value, recipient.taxRegime)
        }
    }
}
