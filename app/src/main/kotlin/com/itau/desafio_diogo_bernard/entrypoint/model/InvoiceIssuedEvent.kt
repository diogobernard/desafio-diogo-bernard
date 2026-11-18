package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class InvoiceIssuedEvent(
    @JsonProperty("evento_id") val eventId: String,
    @JsonProperty("versao") val version: Int,
    @JsonProperty("ocorrido_em") val occurredAt: Instant,
    @JsonProperty("nota") val invoice: InvoiceEventPayload,
)

data class IssuanceRequestReceivedEvent(
    @JsonProperty("evento_id") val eventId: String,
    @JsonProperty("versao") val version: Int,
    @JsonProperty("ocorrido_em") val occurredAt: Instant,
    @JsonProperty("solicitacao_id") val requestId: String,
)
