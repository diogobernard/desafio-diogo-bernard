package com.itau.desafio_diogo_bernard.usecase.processamento

import com.itau.desafio_diogo_bernard.exception.NumberingExhaustedException
import com.itau.desafio_diogo_bernard.gateway.ProcessingGateway
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId

class RecordIssuanceRequestFailureUseCase(
    private val gateway: ProcessingGateway,
) : RecordIssuanceRequestFailure {
    override fun execute(
        id: IssuanceRequestId,
        reason: FailureReason,
    ): Unit = gateway.recordFailure(id, reason)

    override fun execute(
        id: IssuanceRequestId,
        failure: NumberingExhaustedException,
    ) {
        val safeMessage =
            when (failure) {
                is NumberingExhaustedException -> "Não foi possível emitir a nota: numeração fiscal esgotada."
            }
        gateway.recordFailure(id, FailureReason(safeMessage))
    }
}
