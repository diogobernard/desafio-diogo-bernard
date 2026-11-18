package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference

interface PersistedInvoice {
    fun find(reference: InvoiceReference): Invoice?
}

interface InvoicePersistence : PersistedInvoice {
    fun store(invoice: Invoice)
}
