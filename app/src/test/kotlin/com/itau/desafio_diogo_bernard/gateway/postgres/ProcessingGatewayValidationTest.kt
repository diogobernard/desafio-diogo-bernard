package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.entrypoint.solicitacoes.issuanceRequestFixture
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.pedido.PersonType
import com.itau.desafio_diogo_bernard.model.pedido.Recipient
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

class ProcessingGatewayValidationTest {
    @Test
    fun `rejeita destinatario sem documento antes de persistir nota`() {
        val issuanceRequest =
            issuanceRequestFixture().copy(
                order =
                    issuanceRequestFixture().order.copy(
                        recipient = Recipient(PersonType.FISICA),
                    ),
            )
        val gateway = gateway(issuanceRequest)
        val calculated = CalculateInvoice().calculate(issuanceRequest.order, TaxationMode.POR_FORA)

        assertFailsWith<IllegalArgumentException> { gateway.issue(issuanceRequest, calculated) }
    }

    private fun gateway(issuanceRequest: IssuanceRequest): ProcessingPostgresGateway =
        ProcessingPostgresGateway(
            object : IssuanceRequestProcessing {
                override fun start(id: IssuanceRequestId): IssuanceRequest? = issuanceRequest

                override fun lockForIssuance(id: IssuanceRequestId): IssuanceState? =
                    IssuanceState(issuanceRequest.status, issuanceRequest.invoiceReference)

                override fun markIssued(invoice: Invoice): Unit = Unit

                override fun markFailed(
                    id: IssuanceRequestId,
                    reason: FailureReason,
                ): Unit = Unit
            },
            object : NumberingCounter {
                override fun reserve(series: Int): Int = 1
            },
            object : InvoicePersistence {
                override fun store(invoice: Invoice): Unit = Unit

                override fun find(reference: InvoiceReference): Invoice? = null
            },
            object : OutboxEventRecorder {
                override fun prepareRequest(
                    id: UUID,
                    occurredAt: Instant,
                ): NewOutboxEvent = NewOutboxEvent(id, "topico", id.toString(), "{}", null, occurredAt)

                override fun recordIssuedInvoice(
                    invoice: InvoiceResponse,
                    occurredAt: Instant,
                ): Unit = Unit
            },
            NotaFiscalProperties(),
            Clock.systemUTC(),
            BusinessMetrics(SimpleMeterRegistry()),
        )
}
