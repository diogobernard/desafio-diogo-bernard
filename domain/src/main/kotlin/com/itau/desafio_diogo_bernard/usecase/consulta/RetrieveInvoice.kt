package com.itau.desafio_diogo_bernard.usecase.consulta

import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId

interface RetrieveInvoice {
    fun execute(
        clientId: ClientId,
        reference: InvoiceReference,
    ): Invoice
}
