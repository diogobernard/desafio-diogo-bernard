package com.itau.desafio_diogo_bernard.usecase.processamento

import com.itau.desafio_diogo_bernard.exception.NumberingExhaustedException
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId

interface RecordIssuanceRequestFailure {
    fun execute(
        id: IssuanceRequestId,
        reason: FailureReason,
    )

    fun execute(
        id: IssuanceRequestId,
        failure: NumberingExhaustedException,
    )
}
