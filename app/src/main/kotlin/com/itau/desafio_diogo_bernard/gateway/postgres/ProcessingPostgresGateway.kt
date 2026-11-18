package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.entrypoint.model.InvoiceResponse
import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.FiscalNumber
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.nota.InvoiceReference
import com.itau.desafio_diogo_bernard.model.nota.Series
import com.itau.desafio_diogo_bernard.model.pedido.InvoiceRecipient
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class ProcessingPostgresGateway(
    private val requests: IssuanceRequestProcessing,
    private val counter: NumberingCounter,
    private val invoices: InvoicePersistence,
    private val outbox: OutboxEventRecorder,
    private val properties: NotaFiscalProperties,
    private val clock: Clock,
    private val metrics: BusinessMetrics,
) : ProcessingGateway {
    @Transactional
    override fun startProcessing(id: IssuanceRequestId): IssuanceRequest? = requests.start(id)

    @Transactional
    override fun issue(
        issuanceRequest: IssuanceRequest,
        invoice: CalculatedInvoice,
    ): Invoice {
        val state =
            requests.lockForIssuance(issuanceRequest.id) ?: throw IllegalStateException("Solicitação ausente")
        if (state.status == IssuanceRequestStatus.EMITIDA) return existingInvoice(state)
        val number = counter.reserve(properties.numeracao.seriePadrao)
        val issued = createInvoice(issuanceRequest, invoice, number)
        val response = InvoiceResponse.fromDomain(issued)
        invoices.store(issued)
        outbox.recordIssuedInvoice(response, issued.issuedAt)
        requests.markIssued(issued)
        metrics.invoiceIssued(issuanceRequest, issued)
        return issued
    }

    @Transactional
    override fun recordFailure(
        id: IssuanceRequestId,
        reason: FailureReason,
    ) {
        metrics.requestFailed(reason.value)
        requests.markFailed(id, reason)
    }

    private fun existingInvoice(state: IssuanceState): Invoice =
        invoices.find(requireNotNull(state.invoiceReference)) ?: throw IllegalStateException("Nota emitida ausente")

    private fun createInvoice(
        issuanceRequest: IssuanceRequest,
        calculated: CalculatedInvoice,
        number: Int,
    ): Invoice =
        Invoice(
            InvoiceReference(FiscalNumber(number), Series(properties.numeracao.seriePadrao)),
            issuanceRequest.id,
            issuanceRequest.clientId,
            calculated,
            clock.instant(),
            recipient(issuanceRequest),
        )

    private fun recipient(issuanceRequest: IssuanceRequest): InvoiceRecipient {
        val recipient = issuanceRequest.order.recipient
        return InvoiceRecipient(
            recipient.personType,
            requireNotNull(recipient.document).masked(),
            recipient.taxRegime,
        )
    }
}
