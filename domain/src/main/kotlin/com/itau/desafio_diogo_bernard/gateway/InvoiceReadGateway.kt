package com.itau.desafio_diogo_bernard.gateway

import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series

interface InvoiceReadGateway {
    fun find(reference: InvoiceReference): Invoice?

    fun findConsistent(reference: InvoiceReference): Invoice?

    fun lastIssuedNumber(series: Series): FiscalNumber?

    fun lastIssuedNumber(
        series: Series,
        requested: FiscalNumber,
    ): FiscalNumber? = lastIssuedNumber(series)
}
