package com.itau.desafio_diogo_bernard.usecase.emissao

import com.itau.desafio_diogo_bernard.exception.IdempotencyKeyReusedException
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestIdGenerator
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestRegistration
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.FailureReason
import com.itau.desafio_diogo_bernard.model.solicitacao.Fingerprint
import com.itau.desafio_diogo_bernard.model.solicitacao.IdempotencyKey
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.usecase.pedidoValido
import com.itau.desafio_diogo_bernard.usecase.validations.OrderFingerprint
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestIssuanceUseCaseTest {
    @Test
    fun `registra solicitacao nova e reaproveita existente pela impressao digital`() {
        val order = pedidoValido()
        val computedFingerprint = OrderFingerprint().calculate(order)
        val stored =
            IssuanceRequest(
                IssuanceRequestId("id"),
                ClientId("cliente"),
                IdempotencyKey("chave"),
                computedFingerprint,
                order,
                IssuanceRequestStatus.RECEBIDA,
                Instant.EPOCH,
                Instant.EPOCH,
            )
        val gateway = GatewayFake(IssuanceRequestRegistration.Created(stored))
        val useCase = useCase(gateway)
        val command = IssuanceCommand(ClientId("cliente"), IdempotencyKey("chave"), order)

        assertEquals(stored, useCase.execute(command))
        gateway.registration = IssuanceRequestRegistration.Existing(stored)
        assertEquals(stored, useCase.execute(command))
        gateway.registration = IssuanceRequestRegistration.Existing(stored.copy(fingerprint = Fingerprint("outro")))
        assertFailsWith<IdempotencyKeyReusedException> { useCase.execute(command) }
        assertEquals(3, gateway.calls)
    }

    private fun useCase(gateway: GatewayFake): RequestIssuanceUseCase =
        RequestIssuanceUseCase(
            gateway,
            object : IssuanceRequestIdGenerator {
                override fun generate(): IssuanceRequestId = IssuanceRequestId("novo")
            },
            OrderFingerprint(),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )
}

private class GatewayFake(
    var registration: IssuanceRequestRegistration,
) : IssuanceRequestGateway {
    var calls = 0

    override fun registerOnce(issuanceRequest: IssuanceRequest): IssuanceRequestRegistration {
        calls++
        return registration
    }
}
