package com.itau.desafio_diogo_bernard.usecase.consulta

import com.itau.desafio_diogo_bernard.exception.InvoiceNotFoundException
import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice
import com.itau.desafio_diogo_bernard.usecase.pedidoValido
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RetrieveInvoiceUseCaseTest {
    @Test
    fun `usa leitura por serie no contrato com numero solicitado`() {
        val gateway =
            object : InvoiceReadGateway {
                override fun find(reference: InvoiceReference): Invoice? = null

                override fun findConsistent(reference: InvoiceReference): Invoice? = null

                override fun lastIssuedNumber(series: Series): FiscalNumber? = null
            }

        assertNull(gateway.lastIssuedNumber(Series(1), FiscalNumber(2)))
    }

    @Test
    fun `consulta na replica sem fallback quando a nota esta disponivel`() {
        val reference = InvoiceReference(FiscalNumber(5), Series(1))
        val invoice = invoice(reference, ClientId("cliente-a"))
        val gateway = InvoiceReadFake(invoice, null, null)

        assertEquals(invoice, RetrieveInvoiceUseCase(gateway).execute(invoice.clientId, reference))
        assertEquals(0, gateway.primaryQueries)
    }

    @Test
    fun `consulta nota com fallback ate o ultimo numero e isolamento por cliente`() {
        val reference = InvoiceReference(FiscalNumber(5), Series(1))
        val invoice = invoice(reference, ClientId("cliente-a"))
        val gateway = InvoiceReadFake(null, invoice, FiscalNumber(6))
        val useCase = RetrieveInvoiceUseCase(gateway)

        assertEquals(invoice, useCase.execute(invoice.clientId, reference))
        assertEquals(1, gateway.primaryQueries)
        val replica = InvoiceReadFake(invoice, invoice, FiscalNumber(6))
        assertFailsWith<InvoiceNotFoundException> {
            RetrieveInvoiceUseCase(
                replica,
            ).execute(ClientId("cliente-b"), reference)
        }
        assertEquals(0, replica.primaryQueries)
        assertFailsWith<InvoiceNotFoundException> {
            useCase.execute(invoice.clientId, InvoiceReference(FiscalNumber(7), Series(1)))
        }
        assertEquals(1, gateway.primaryQueries)
    }

    @Test
    fun `nao consulta primario se nao existe contador ou numero pedido excede o ultimo`() {
        val reference = InvoiceReference(FiscalNumber(5), Series(1))
        val withoutCounter = InvoiceReadFake(null, null, null)
        val aboveLast = InvoiceReadFake(null, null, FiscalNumber(4))

        assertFailsWith<InvoiceNotFoundException> {
            RetrieveInvoiceUseCase(withoutCounter).execute(ClientId("cliente"), reference)
        }
        assertFailsWith<InvoiceNotFoundException> {
            RetrieveInvoiceUseCase(aboveLast).execute(ClientId("cliente"), reference)
        }
        assertEquals(0, withoutCounter.primaryQueries)
        assertEquals(0, aboveLast.primaryQueries)
    }

    @Test
    fun `retorna nao encontrada quando contador permite leitura mas primario nao tem nota`() {
        val reference = InvoiceReference(FiscalNumber(5), Series(1))
        val gateway = InvoiceReadFake(null, null, FiscalNumber(5))

        assertFailsWith<InvoiceNotFoundException> {
            RetrieveInvoiceUseCase(gateway).execute(ClientId("cliente"), reference)
        }
        assertEquals(1, gateway.primaryQueries)
    }

    private fun invoice(
        reference: InvoiceReference,
        client: ClientId,
    ): Invoice {
        val order = pedidoValido()
        val calculated = CalculateInvoice().calculate(order, TaxationMode.POR_FORA)
        return Invoice(
            reference,
            IssuanceRequestId("id"),
            client,
            calculated,
            Instant.EPOCH,
            InvoiceRecipient(
                order.recipient.personType,
                requireNotNull(order.recipient.document).masked(),
                order.recipient.taxRegime,
            ),
        )
    }
}

private class InvoiceReadFake(
    private val replica: Invoice?,
    private val primary: Invoice?,
    private val last: FiscalNumber?,
) : InvoiceReadGateway {
    var primaryQueries = 0

    override fun find(reference: InvoiceReference): Invoice? = replica

    override fun findConsistent(reference: InvoiceReference): Invoice? {
        primaryQueries++
        return primary
    }

    override fun lastIssuedNumber(series: Series): FiscalNumber? = last

    override fun lastIssuedNumber(
        series: Series,
        requested: FiscalNumber,
    ): FiscalNumber? = last
}
