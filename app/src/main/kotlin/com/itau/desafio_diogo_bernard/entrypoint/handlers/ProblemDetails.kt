package com.itau.desafio_diogo_bernard.entrypoint.handlers

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.usecase.validations.Violation
import com.itau.desafio_diogo_bernard.usecase.validations.ViolationType

data class ProblemDetails(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val traceId: String,
    val errors: List<ViolationResponse> = emptyList(),
)

data class ViolationResponse(
    @JsonProperty("field") val field: String,
    val code: String,
    @JsonProperty("message") val message: String,
) {
    companion object {
        private const val FIELD_TAX_REGIME = "destinatario.regime_tributacao"
        private const val FIELD_ITEMS = "itens"
        private const val FIELD_ADDRESSES = "enderecos"

        fun fromDomain(violation: Violation): ViolationResponse {
            val code =
                when (violation.type) {
                    ViolationType.AUSENTE -> {
                        "required_field"
                    }

                    ViolationType.MALFORMADO -> {
                        when (violation.field) {
                            FIELD_ITEMS -> "empty_items"
                            FIELD_ADDRESSES -> "missing_delivery_address"
                            else -> "invalid_value"
                        }
                    }

                    ViolationType.IMPEDITIVO -> {
                        if (violation.field == FIELD_TAX_REGIME) "unsupported_tax_regime" else "item_limit_exceeded"
                    }
                }
            return ViolationResponse(externalField(violation.field), code, violation.message)
        }

        private fun externalField(field: String): String =
            field
                .replace("destinatario", "recipient")
                .replace("tipo_pessoa", "person_type")
                .replace("documento", "document")
                .replace("regime_tributacao", "tax_regime")
                .replace("itens", "items")
                .replace("codigo", "code")
                .replace("descricao", "description")
                .replace("valor_unitario", "unit_price")
                .replace("quantidade", "quantity")
                .replace("valor_frete", "freight_value")
                .replace("enderecos", "addresses")
                .replace("finalidade", "purpose")
                .replace("regiao", "region")
    }
}
