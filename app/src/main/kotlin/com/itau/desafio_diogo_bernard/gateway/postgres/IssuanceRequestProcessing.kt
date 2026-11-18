package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId

interface IssuanceRequestProcessing {
    fun start(id: IssuanceRequestId): IssuanceRequest?

    fun lockForIssuance(id: IssuanceRequestId): IssuanceState?

    fun markIssued(invoice: Invoice)

    fun markFailed(
        id: IssuanceRequestId,
        reason: FailureReason,
    )
}
