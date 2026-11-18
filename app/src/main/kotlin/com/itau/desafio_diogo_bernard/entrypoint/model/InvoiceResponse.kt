package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import java.math.BigDecimal
import java.time.Instant

data class InvoiceResponse(
    @JsonProperty("number") val number: Int,
    @JsonProperty("series") val series: Int,
    @JsonProperty("request_id") val requestId: String,
    @JsonProperty("issued_at") val issuedAt: Instant,
    @JsonProperty("taxation_mode") val taxationMode: TaxationMode,
    @JsonProperty("tax_rate") val taxRate: BigDecimal,
    @JsonProperty("recipient") val recipient: InvoiceRecipientResponse,
    @JsonProperty("items") val items: List<InvoiceItemResponse>,
    @JsonProperty("freight") val freight: FreightResponse,
    @JsonProperty("totals") val totals: InvoiceTotalsResponse,
) {
    companion object {
        fun fromDomain(invoice: Invoice): InvoiceResponse {
            val calculated = invoice.calculated
            val recipient = invoice.recipient
            return InvoiceResponse(
                invoice.reference.number.value,
                invoice.reference.series.value,
                invoice.requestId.value,
                invoice.issuedAt,
                calculated.mode,
                calculated.taxRate.value,
                InvoiceRecipientResponse.fromDomain(recipient),
                calculated.items.map(InvoiceItemResponse::fromDomain),
                FreightResponse(
                    calculated.freightValue.value,
                    calculated.deliveryRegion,
                    calculated.freightPercentage.value,
                    calculated.freight.value.subtract(calculated.freightValue.value),
                    calculated.freight.value,
                ),
                InvoiceTotalsResponse(
                    calculated.itemsTotal.value,
                    calculated.tax.value,
                    calculated.freight.value,
                    calculated.invoiceTotal.value,
                ),
            )
        }
    }
}
