package com.itau.desafio_diogo_bernard.gateway

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId

interface IssuanceRequestIdGenerator {
    fun generate(): IssuanceRequestId
}
