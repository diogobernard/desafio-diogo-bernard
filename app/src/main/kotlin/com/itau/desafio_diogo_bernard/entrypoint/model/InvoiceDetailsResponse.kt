package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.nota.CalculatedItem
import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import java.math.BigDecimal

data class InvoiceRecipientResponse(
    @JsonProperty("person_type") val personType: PersonType,
    @JsonProperty("masked_document") val maskedDocument: String,
    @JsonProperty("tax_regime") val taxRegime: TaxRegime?,
) {
    companion object {
        fun fromDomain(value: InvoiceRecipient): InvoiceRecipientResponse =
            InvoiceRecipientResponse(value.personType, value.maskedDocument, value.taxRegime)
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InvoiceItemResponse(
    @JsonProperty("code") val code: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("unit_price") val unitValue: BigDecimal,
    @JsonProperty("quantity") val quantity: Int,
    @JsonProperty("item_total") val itemTotal: BigDecimal,
    @JsonProperty("item_tax") val itemTax: BigDecimal,
    @JsonProperty("net_value") val netValue: BigDecimal? = null,
) {
    companion object {
        fun fromDomain(item: CalculatedItem): InvoiceItemResponse =
            InvoiceItemResponse(
                item.code,
                item.description,
                item.unitValue.value,
                item.quantity,
                item.itemTotal.value,
                item.itemTax.value,
                item.netValue?.value,
            )
    }
}

data class FreightResponse(
    @JsonProperty("provided_value") val providedValue: BigDecimal,
    @JsonProperty("region") val region: Region,
    @JsonProperty("percentage") val percentage: BigDecimal,
    @JsonProperty("surcharge") val increment: BigDecimal,
    val total: BigDecimal,
)

data class InvoiceTotalsResponse(
    @JsonProperty("items_total") val itemsTotal: BigDecimal,
    @JsonProperty("tax") val tax: BigDecimal,
    @JsonProperty("freight") val freight: BigDecimal,
    @JsonProperty("invoice_total") val invoiceTotal: BigDecimal,
)
