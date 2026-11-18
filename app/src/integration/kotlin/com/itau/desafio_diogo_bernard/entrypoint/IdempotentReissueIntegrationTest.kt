package com.itau.desafio_diogo_bernard.entrypoint

import com.itau.desafio_diogo_bernard.entrypoint.model.AddressRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.IssueInvoiceRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.ItemRequest
import com.itau.desafio_diogo_bernard.entrypoint.model.RecipientRequest
import com.itau.desafio_diogo_bernard.gateway.InvoiceReadGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestReadGateway
import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.AddressPurpose
import com.itau.desafio_diogo_bernard.model.pedido.Order
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Region
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals

@Tag("integration")
class IdempotentReissueIntegrationTest : PostgresIntegrationBase() {
    @Autowired
    private lateinit var processing: ProcessingGateway

    @Autowired
    private lateinit var invoiceReads: InvoiceReadGateway

    @Autowired
    private lateinit var requestReads: IssuanceRequestReadGateway

    @Test
    fun `reentrega da emissao retorna a nota persistida existente`() {
        val id = criarSolicitacaoParaConsumidores()
        val requestId = IssuanceRequestId(id)
        val order = order()
        val calculated = CalculateInvoice().calculate(order, TaxationMode.POR_FORA)
        await.atMost(30, SECONDS).untilAsserted {
            assertEquals(
                1,
                jdbc
                    .sql(
                        "SELECT count(*) FROM nota_fiscal WHERE solicitacao_id=:id",
                    ).param("id", UUID.fromString(id))
                    .query(Int::class.java)
                    .single(),
            )
        }

        val issuanceRequest = novaSolicitacao().copy(id = requestId, order = order)
        val previousInvoice = processing.issue(issuanceRequest, calculated)
        val redeliveredInvoice = processing.issue(issuanceRequest, calculated)
        val readInvoice = invoiceReads.findConsistent(previousInvoice.reference)
        val readRequest = requestReads.findConsistent(requestId)

        assertEquals(previousInvoice, redeliveredInvoice)
        assertEquals(previousInvoice, readInvoice)
        assertEquals(previousInvoice.reference, readRequest?.invoiceReference)
        assertEquals(null, readRequest?.failureReason)
    }

    private fun order(): Order =
        IssueInvoiceRequest(
            RecipientRequest(PersonType.FISICA, "52998224725", null),
            listOf(ItemRequest("SKU-1", "Produto", BigDecimal("12.50"), BigDecimal.ONE)),
            BigDecimal("2.00"),
            listOf(AddressRequest(AddressPurpose.ENTREGA, Region.SUDESTE)),
        ).toDomain()
}
