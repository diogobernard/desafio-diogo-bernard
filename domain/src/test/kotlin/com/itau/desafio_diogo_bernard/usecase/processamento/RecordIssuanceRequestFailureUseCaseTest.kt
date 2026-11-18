package com.itau.desafio_diogo_bernard.usecase.processamento

import com.itau.desafio_diogo_bernard.exception.NumberingExhaustedException
import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.nota.CalculatedInvoice
import com.itau.desafio_diogo_bernard.model.nota.Invoice
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RecordIssuanceRequestFailureUseCaseTest {
    @Test
    fun `registra motivo de falha recebido sem alteracao`() {
        var receivedValue: FailureReason? = null
        val gateway =
            object : ProcessingGateway {
                override fun startProcessing(id: IssuanceRequestId): IssuanceRequest? = null

                override fun issue(
                    issuanceRequest: IssuanceRequest,
                    invoice: CalculatedInvoice,
                ): Invoice = error("Não esperado")

                override fun recordFailure(
                    id: IssuanceRequestId,
                    reason: FailureReason,
                ) {
                    receivedValue = reason
                }
            }
        val reason = FailureReason("Falha de processamento")

        RecordIssuanceRequestFailureUseCase(gateway).execute(IssuanceRequestId("solicitacao"), reason)

        assertEquals(reason, receivedValue)
    }

    @Test
    fun `registra falha com motivo legivel`() {
        var receivedReason: FailureReason? = null
        val gateway =
            object : ProcessingGateway {
                override fun startProcessing(id: IssuanceRequestId): IssuanceRequest? = null

                override fun issue(
                    issuanceRequest: IssuanceRequest,
                    invoice: CalculatedInvoice,
                ): Invoice = error("Não esperado")

                override fun recordFailure(
                    id: IssuanceRequestId,
                    reason: FailureReason,
                ) {
                    receivedReason = reason
                }
            }
        RecordIssuanceRequestFailureUseCase(gateway).execute(IssuanceRequestId("id"), NumberingExhaustedException())

        assertEquals("Não foi possível emitir a nota: numeração fiscal esgotada.", receivedReason?.value)
    }
}
