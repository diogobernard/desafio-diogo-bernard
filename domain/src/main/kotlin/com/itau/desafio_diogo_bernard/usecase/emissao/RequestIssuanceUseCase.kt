package com.itau.desafio_diogo_bernard.usecase.emissao

import com.itau.desafio_diogo_bernard.exception.IdempotencyKeyReusedException
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestIdGenerator
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestRegistration
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.usecase.validations.OrderFingerprint
import java.time.Clock

class RequestIssuanceUseCase(
    private val gateway: IssuanceRequestGateway,
    private val idGenerator: IssuanceRequestIdGenerator,
    private val orderFingerprint: OrderFingerprint,
    private val clock: Clock,
) : RequestIssuance {
    override fun execute(command: IssuanceCommand): IssuanceRequest {
        val computedFingerprint = orderFingerprint.calculate(command.order)
        val issuanceRequest = createRequest(command, computedFingerprint)
        return when (val registration = gateway.registerOnce(issuanceRequest)) {
            is IssuanceRequestRegistration.Created -> registration.issuanceRequest
            is IssuanceRequestRegistration.Existing -> reuse(registration, computedFingerprint)
        }
    }

    private fun createRequest(
        command: IssuanceCommand,
        computedFingerprint: Fingerprint,
    ): IssuanceRequest {
        val now = clock.instant()
        return IssuanceRequest(
            idGenerator.generate(),
            command.clientId,
            command.key,
            computedFingerprint,
            command.order,
            IssuanceRequestStatus.RECEBIDA,
            now,
            now,
        )
    }

    private fun reuse(
        registration: IssuanceRequestRegistration.Existing,
        computedFingerprint: Fingerprint,
    ): IssuanceRequest {
        if (registration.issuanceRequest.fingerprint != computedFingerprint) throw IdempotencyKeyReusedException()
        return registration.issuanceRequest
    }
}
