package com.itau.desafio_diogo_bernard.model.solicitacao

import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.pedido.Order
import java.time.Instant

data class IssuanceRequest(
    val id: IssuanceRequestId,
    val clientId: ClientId,
    val key: IdempotencyKey,
    val fingerprint: Fingerprint,
    val order: Order,
    val status: IssuanceRequestStatus,
    val receivedAt: Instant,
    val updatedAt: Instant,
    val invoiceReference: InvoiceReference? = null,
    val failureReason: FailureReason? = null,
) {
    fun status(): IssuanceRequestState =
        IssuanceRequestState(
            id,
            clientId,
            status,
            receivedAt,
            updatedAt,
            invoiceReference,
            failureReason,
        )
}
