package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class IssuanceRequestResponse(
    val id: String,
    val status: IssuanceRequestStatus,
    @JsonProperty("received_at") val receivedAt: Instant,
    @JsonProperty("updated_at") val updatedAt: Instant,
    @JsonProperty("invoice") val invoice: InvoiceSummaryResponse? = null,
    @JsonProperty("reason") val reason: String? = null,
    val links: IssuanceRequestLinks,
) {
    companion object {
        fun fromDomain(issuanceRequest: IssuanceRequestState): IssuanceRequestResponse {
            val reference = issuanceRequest.invoiceReference
            val invoice = reference?.let {
                InvoiceSummaryResponse(
                    it.number.value,
                    it.series.value,
                    issuanceRequest.updatedAt,
                )
            }
            val self = "/issuance-requests/${issuanceRequest.id.value}"
            val invoiceLink = reference?.let {
                "/invoices/${it.number.value}?series=${it.series.value}"
            }
            return IssuanceRequestResponse(
                issuanceRequest.id.value,
                issuanceRequest.status,
                issuanceRequest.receivedAt,
                issuanceRequest.updatedAt,
                invoice,
                issuanceRequest.failureReason?.value,
                IssuanceRequestLinks(self, invoiceLink),
            )
        }
    }
}

data class InvoiceSummaryResponse(
    @JsonProperty("number") val number: Int,
    @JsonProperty("series") val series: Int,
    @JsonProperty("issued_at") val issuedAt: Instant,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class IssuanceRequestLinks(
    val self: String,
    @JsonProperty("invoice") val invoice: String? = null,
)
