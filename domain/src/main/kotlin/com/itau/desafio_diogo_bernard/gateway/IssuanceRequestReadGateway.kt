package com.itau.desafio_diogo_bernard.gateway

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState

interface IssuanceRequestReadGateway {
    fun find(id: IssuanceRequestId): IssuanceRequestState?

    fun findConsistent(id: IssuanceRequestId): IssuanceRequestState?
}
