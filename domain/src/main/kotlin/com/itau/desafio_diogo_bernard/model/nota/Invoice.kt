package com.itau.desafio_diogo_bernard.model.nota

import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import java.time.Instant

data class Invoice(
    val reference: InvoiceReference,
    val requestId: IssuanceRequestId,
    val clientId: ClientId,
    val calculated: CalculatedInvoice,
    val issuedAt: Instant,
    val recipient: InvoiceRecipient,
)
