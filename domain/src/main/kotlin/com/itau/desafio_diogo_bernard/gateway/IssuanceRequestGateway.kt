package com.itau.desafio_diogo_bernard.gateway

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest

sealed interface IssuanceRequestRegistration {
    data class Created(
        val issuanceRequest: IssuanceRequest,
    ) : IssuanceRequestRegistration

    data class Existing(
        val issuanceRequest: IssuanceRequest,
    ) : IssuanceRequestRegistration
}

interface IssuanceRequestGateway {
    fun registerOnce(issuanceRequest: IssuanceRequest): IssuanceRequestRegistration
}
