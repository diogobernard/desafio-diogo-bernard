package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus

data class IssuanceState(
    val status: IssuanceRequestStatus,
    val invoiceReference: InvoiceReference?,
)
