package com.itau.desafio_diogo_bernard.usecase.processamento

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId

interface ProcessIssuanceRequest {
    fun execute(id: IssuanceRequestId)
}
