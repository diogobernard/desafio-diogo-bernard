package com.itau.desafio_diogo_bernard.usecase.consulta

import com.itau.desafio_diogo_bernard.exception.IssuanceRequestNotFoundException
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestReadGateway
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState
import com.itau.desafio_diogo_bernard.usecase.validations.ConsistentReadPolicy

class RetrieveIssuanceRequestUseCase(
    private val gateway: IssuanceRequestReadGateway,
    private val policy: ConsistentReadPolicy,
) : RetrieveIssuanceRequest {
    override fun execute(
        clientId: ClientId,
        id: IssuanceRequestId,
    ): IssuanceRequestState {
        val found = gateway.find(id) ?: findConsistentIfRecent(id)
        if (found.clientId != clientId) throw IssuanceRequestNotFoundException(id)
        return found
    }

    private fun findConsistentIfRecent(id: IssuanceRequestId): IssuanceRequestState =
        if (policy.allowsFallback(id)) {
            gateway.findConsistent(id) ?: throw IssuanceRequestNotFoundException(id)
        } else {
            throw IssuanceRequestNotFoundException(id)
        }
}
