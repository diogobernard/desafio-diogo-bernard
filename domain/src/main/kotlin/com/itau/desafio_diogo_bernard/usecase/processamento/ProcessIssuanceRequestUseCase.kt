package com.itau.desafio_diogo_bernard.usecase.processamento

import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.nota.TaxationMode
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.usecase.calculo.CalculateInvoice

class ProcessIssuanceRequestUseCase(
    private val gateway: ProcessingGateway,
    private val calculator: CalculateInvoice,
    private val mode: TaxationMode,
) : ProcessIssuanceRequest {
    override fun execute(id: IssuanceRequestId) {
        val issuanceRequest = gateway.startProcessing(id) ?: return
        val invoice = calculator.calculate(issuanceRequest.order, mode)
        gateway.issue(issuanceRequest, invoice)
    }
}
