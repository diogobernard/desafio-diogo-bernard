package com.itau.desafio_diogo_bernard.usecase.consulta

import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState

interface RetrieveIssuanceRequest {
    fun execute(
        clientId: ClientId,
        id: IssuanceRequestId,
    ): IssuanceRequestState
}
