package com.itau.desafio_diogo_bernard.usecase.processamento

import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
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

class ProcessIssuanceRequestUseCaseTest {
    @Test
    fun `processa solicitacao e ignora reentrega ja emitida`() {
        val order = pedidoValido()
        val issuanceRequest =
            IssuanceRequest(
                IssuanceRequestId("id"),
                ClientId("cliente"),
                IdempotencyKey("chave"),
                Fingerprint("hash"),
                order,
                IssuanceRequestStatus.PROCESSANDO,
                Instant.EPOCH,
                Instant.EPOCH,
            )
        val gateway =
            object : ProcessingGateway {
                var issuedRows = 0

                override fun startProcessing(id: IssuanceRequestId): IssuanceRequest? = issuanceRequest

                override fun issue(
                    issuanceRequest: IssuanceRequest,
                    invoice: CalculatedInvoice,
                ): Invoice {
                    issuedRows++
                    return Invoice(
                        InvoiceReference(FiscalNumber(1), Series(1)),
                        issuanceRequest.id,
                        issuanceRequest.clientId,
                        invoice,
                        Instant.EPOCH,
                        InvoiceRecipient(
                            issuanceRequest.order.recipient.personType,
                            requireNotNull(issuanceRequest.order.recipient.document).masked(),
                            issuanceRequest.order.recipient.taxRegime,
                        ),
                    )
                }

                override fun recordFailure(
                    id: IssuanceRequestId,
                    reason: FailureReason,
                ): Unit = Unit
            }
        ProcessIssuanceRequestUseCase(gateway, CalculateInvoice(), TaxationMode.POR_FORA).execute(issuanceRequest.id)
        assertEquals(1, gateway.issuedRows)

        val redelivery =
            object : ProcessingGateway by gateway {
                override fun startProcessing(id: IssuanceRequestId): IssuanceRequest? = null

                override fun issue(
                    issuanceRequest: IssuanceRequest,
                    invoice: CalculatedInvoice,
                ): Invoice = error("Reentrega emitida")
            }
        ProcessIssuanceRequestUseCase(redelivery, CalculateInvoice(), TaxationMode.POR_FORA).execute(issuanceRequest.id)
    }
}
