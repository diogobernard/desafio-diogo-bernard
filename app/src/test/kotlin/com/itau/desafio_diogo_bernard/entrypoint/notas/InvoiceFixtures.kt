package com.itau.desafio_diogo_bernard.entrypoint.notas

import com.itau.desafio_diogo_bernard.model.dinheiro.Money
import com.itau.desafio_diogo_bernard.model.dinheiro.TaxRate
import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import java.time.Instant

internal fun notaFiscalFixture(client: String = "cliente-a"): Invoice =
    Invoice(
        InvoiceReference(FiscalNumber(77), Series(1)),
        IssuanceRequestId("01933b6e-7c1a-7001-9f10-2b6a4c8d0e11"),
        ClientId(client),
        CalculatedInvoice(
            emptyList(),
            Money.ZERO,
            Money.ZERO,
            TaxRate.percentage("0"),
            Money.ZERO,
            TaxRate.percentage("0"),
            Money.ZERO,
            Money.ZERO,
            TaxationMode.POR_FORA,
            Region.SUL,
        ),
        Instant.EPOCH,
        InvoiceRecipient(PersonType.FISICA, "***.456.789-**", null),
    )
