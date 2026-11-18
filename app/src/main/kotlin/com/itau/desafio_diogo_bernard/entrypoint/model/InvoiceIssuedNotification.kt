package com.itau.desafio_diogo_bernard.entrypoint.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Leitura do evento NotaEmitida pelos consumidores: só a identificação da nota. Itens, valores e
 * destinatário são ignorados na desserialização, sem criar objetos nem `BigDecimal` para eles.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InvoiceIssuedNotification(
    @JsonProperty("evento_id") val eventId: String,
    @JsonProperty("ocorrido_em") val occurredAt: Instant,
    @JsonProperty("nota") val invoice: InvoiceIssuedReference,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InvoiceIssuedReference(
    @JsonProperty("numero") val number: Int,
    @JsonProperty("serie") val series: Int,
    @JsonProperty("solicitacao_id") val requestId: String,
)
