package com.itau.desafio_diogo_bernard.gateway

import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId

interface ProcessingGateway {
    fun startProcessing(id: IssuanceRequestId): IssuanceRequest?

    /** Emite a nota da solicitação já iniciada; o destinatário vem dela, sem reler o pedido. */
    fun issue(
        issuanceRequest: IssuanceRequest,
        invoice: CalculatedInvoice,
    ): Invoice

    fun recordFailure(
        id: IssuanceRequestId,
        reason: FailureReason,
    )
}
