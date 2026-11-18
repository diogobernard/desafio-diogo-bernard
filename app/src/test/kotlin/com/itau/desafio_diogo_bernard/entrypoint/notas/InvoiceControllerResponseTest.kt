package com.itau.desafio_diogo_bernard.entrypoint.notas

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveInvoice
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class InvoiceControllerResponseTest {
    @Test
    fun `retorna nota sem avaliacao condicional quando etag nao foi enviado`() {
        val invoice = notaFiscalFixture()
        val controller =
            InvoiceController(
                object : RetrieveInvoice {
                    override fun execute(
                        clientId: ClientId,
                        reference: InvoiceReference,
                    ): Invoice = invoice
                },
                NotaFiscalProperties(),
                BusinessMetrics(SimpleMeterRegistry()),
            )

        val response = controller.retrieve("cliente-a", "77", "1", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("77", response.body?.number?.toString())
    }

    @Test
    fun `retorna nota quando etag enviado esta vazio`() {
        val controller =
            InvoiceController(
                object : RetrieveInvoice {
                    override fun execute(
                        clientId: ClientId,
                        reference: InvoiceReference,
                    ): Invoice = notaFiscalFixture()
                },
                NotaFiscalProperties(),
                BusinessMetrics(SimpleMeterRegistry()),
            )

        assertEquals(HttpStatus.OK, controller.retrieve("cliente-a", "77", "1", "").statusCode)
    }

    @Test
    fun `retorna nota com serie padrao e etag quando nao coincide`() {
        val registry = SimpleMeterRegistry()
        val invoice = notaFiscalFixture()
        var receivedReference: InvoiceReference? = null
        val controller =
            InvoiceController(
                object : RetrieveInvoice {
                    override fun execute(
                        clientId: ClientId,
                        reference: InvoiceReference,
                    ): Invoice = invoice.also { receivedReference = reference }
                },
                NotaFiscalProperties(),
                BusinessMetrics(registry),
            )

        val response = controller.retrieve(" cliente-a ", "77", null, "\"1-76\"")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(InvoiceReference(invoice.reference.number, invoice.reference.series), receivedReference)
        assertEquals("\"1-77\"", response.headers.getFirst(HttpHeaders.ETAG))
        assertEquals("max-age=86400, private, immutable", response.headers.cacheControl)
        assertEquals("77", response.body?.number?.toString())
    }

    @Test
    fun `retorna 304 para um etag entre varios valores enviados`() {
        val controller =
            InvoiceController(
                object : RetrieveInvoice {
                    override fun execute(
                        clientId: ClientId,
                        reference: InvoiceReference,
                    ): Invoice = notaFiscalFixture()
                },
                NotaFiscalProperties(),
                BusinessMetrics(SimpleMeterRegistry()),
            )

        val response = controller.retrieve("cliente-a", "77", "1", "\"1-76\", \"1-77\"")

        assertEquals(HttpStatus.NOT_MODIFIED, response.statusCode)
        assertEquals(null, response.body)
    }
}
