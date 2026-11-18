package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.pedido.TaxRegime
import java.math.BigDecimal
import java.time.Instant

data class InvoiceEventPayload(
    @JsonProperty("numero") val number: Int,
    @JsonProperty("serie") val series: Int,
    @JsonProperty("solicitacao_id") val requestId: String,
    @JsonProperty("emitida_em") val issuedAt: Instant,
    @JsonProperty("modo_tributacao") val taxationMode: TaxationMode,
    @JsonProperty("aliquota") val taxRate: BigDecimal,
    @JsonProperty("destinatario") val recipient: InvoiceEventRecipient,
    @JsonProperty("itens") val items: List<InvoiceEventItem>,
    @JsonProperty("frete") val freight: InvoiceEventFreight,
    @JsonProperty("totais") val totals: InvoiceEventTotals,
) {
    companion object {
        fun from(response: InvoiceResponse): InvoiceEventPayload =
            InvoiceEventPayload(
                response.number,
                response.series,
                response.requestId,
                response.issuedAt,
                response.taxationMode,
                response.taxRate,
                InvoiceEventRecipient(
                    response.recipient.personType,
                    response.recipient.maskedDocument,
                    response.recipient.taxRegime,
                ),
                response.items.map {
                    InvoiceEventItem(
                        it.code,
                        it.description,
                        it.unitValue,
                        it.quantity,
                        it.itemTotal,
                        it.itemTax,
                        it.netValue,
                    )
                },
                InvoiceEventFreight(
                    response.freight.providedValue,
                    response.freight.region,
                    response.freight.percentage,
                    response.freight.increment,
                    response.freight.total,
                ),
                InvoiceEventTotals(
                    response.totals.itemsTotal,
                    response.totals.tax,
                    response.totals.freight,
                    response.totals.invoiceTotal,
                ),
            )
    }
}

data class InvoiceEventRecipient(
    @JsonProperty("tipo_pessoa") val personType: PersonType,
    @JsonProperty("documento_mascarado") val maskedDocument: String,
    @JsonProperty("regime_tributacao") val taxRegime: TaxRegime?,
)

data class InvoiceEventItem(
    @JsonProperty("codigo") val code: String,
    @JsonProperty("descricao") val description: String,
    @JsonProperty("valor_unitario") val unitValue: BigDecimal,
    @JsonProperty("quantidade") val quantity: Int,
    @JsonProperty("total_item") val itemTotal: BigDecimal,
    @JsonProperty("tributo_item") val itemTax: BigDecimal,
    @JsonProperty("valor_liquido") val netValue: BigDecimal?,
)

data class InvoiceEventFreight(
    @JsonProperty("valor_informado") val providedValue: BigDecimal,
    @JsonProperty("regiao") val region: Region,
    @JsonProperty("percentual") val percentage: BigDecimal,
    @JsonProperty("acrescimo") val increment: BigDecimal,
    val total: BigDecimal,
)

data class InvoiceEventTotals(
    @JsonProperty("total_itens") val itemsTotal: BigDecimal,
    @JsonProperty("tributo") val tax: BigDecimal,
    @JsonProperty("frete") val freight: BigDecimal,
    @JsonProperty("total_nota") val invoiceTotal: BigDecimal,
)
