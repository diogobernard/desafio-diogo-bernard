package com.itau.desafio_diogo_bernard.entrypoint.notas

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.exception.InvoiceNotFoundException
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.usecase.consulta.RetrieveInvoice
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvoiceControllerValidationTest {
    private val registry = SimpleMeterRegistry()
    private val metrics = BusinessMetrics(registry)
    private val controller = controller { _, _ -> notaFiscalFixture() }

    @Test
    fun `rejeita clientes vazios e maiores que o limite`() {
        assertEquals(HttpStatus.UNAUTHORIZED, failure(client = null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, failure(client = "").statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, failure(client = "   ").statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, failure(client = "a".repeat(65)).statusCode)
        val controllerAtLimit = controller { _, _ -> notaFiscalFixture() }
        assertEquals(
            HttpStatus.OK,
            controllerAtLimit.retrieve("a".repeat(64), "77", "1", null).statusCode,
        )
    }

    @Test
    fun `rejeita numero e serie invalidos`() {
        assertEquals(HttpStatus.NOT_FOUND, failure("x").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, failure("77", "abc").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, failure("0").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, failure("77", "1000").statusCode)
    }

    @Test
    fun `registra leitura como ausente e propaga nao encontrada`() {
        val notFound = controller { _, _ -> throw InvoiceNotFoundException() }
        assertFailsWith<InvoiceNotFoundException> { notFound.retrieve("cliente-a", "77", null, null) }
        metrics.recordSuccessfulRead("1-77")
        assertEquals(1.0, registry.counter("notafiscal.leitura.404_falso").count())
    }

    private fun failure(
        number: String = "77",
        series: String? = null,
        client: String? = "cliente-a",
    ): ResponseStatusException = assertFailsWith { controller.retrieve(client, number, series, null) }

    private fun controller(find: (ClientId, InvoiceReference) -> Invoice): InvoiceController =
        InvoiceController(
            object : RetrieveInvoice {
                override fun execute(
                    clientId: ClientId,
                    reference: InvoiceReference,
                ): Invoice = find(clientId, reference)
            },
            NotaFiscalProperties(),
            metrics,
        )
}
