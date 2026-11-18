package com.itau.desafio_diogo_bernard.usecase.consulta

import com.itau.desafio_diogo_bernard.exception.InvoiceNotFoundException
import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId

class RetrieveInvoiceUseCase(
    private val gateway: InvoiceReadGateway,
) : RetrieveInvoice {
    override fun execute(
        clientId: ClientId,
        reference: InvoiceReference,
    ): Invoice {
        val invoice = gateway.find(reference) ?: fallback(reference)
        if (invoice.clientId != clientId) throw InvoiceNotFoundException()
        return invoice
    }

    private fun fallback(reference: InvoiceReference): Invoice {
        val last = gateway.lastIssuedNumber(reference.series, reference.number)
        if (last == null || reference.number > last) throw InvoiceNotFoundException()
        return gateway.findConsistent(reference) ?: throw InvoiceNotFoundException()
    }
}
