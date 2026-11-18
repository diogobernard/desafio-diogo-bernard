package com.itau.desafio_diogo_bernard.model.solicitacao

import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import java.time.Instant

/** Estado observável de uma solicitação, sem o pedido: é o que a consulta precisa devolver. */
data class IssuanceRequestState(
    val id: IssuanceRequestId,
    val clientId: ClientId,
    val status: IssuanceRequestStatus,
    val receivedAt: Instant,
    val updatedAt: Instant,
    val invoiceReference: InvoiceReference? = null,
    val failureReason: FailureReason? = null,
)
