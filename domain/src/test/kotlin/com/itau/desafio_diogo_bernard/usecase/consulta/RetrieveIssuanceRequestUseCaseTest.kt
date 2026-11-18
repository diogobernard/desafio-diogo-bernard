package com.itau.desafio_diogo_bernard.usecase.consulta

import com.itau.desafio_diogo_bernard.exception.IssuanceRequestNotFoundException
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestReadGateway
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestState
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestStatus
import com.itau.desafio_diogo_bernard.usecase.validations.ConsistentReadPolicy
import com.itau.desafio_diogo_bernard.usecase.validations.idV7
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetrieveIssuanceRequestUseCaseTest {
    @Test
    fun `aplica fallback para solicitacao recente e rejeita id antigo sem primario`() {
        val now = Instant.parse("2026-11-14T12:00:00Z")
        val recentId = idV7(now.minusSeconds(2))
        val issuanceRequest = issuanceRequest(recentId, ClientId("cliente"))
        val gateway = SolicitationReadFake(null, issuanceRequest)
        val useCase = RetrieveIssuanceRequestUseCase(gateway, policy(now))

        assertEquals(issuanceRequest, useCase.execute(issuanceRequest.clientId, recentId))
        assertEquals(1, gateway.primaryQueries)
        assertFailsWith<IssuanceRequestNotFoundException> {
            useCase.execute(issuanceRequest.clientId, idV7(now.minusSeconds(120)))
        }
        assertEquals(1, gateway.primaryQueries)
    }

    @Test
    fun `isola cliente e retorna resultado encontrado na replica`() {
        val now = Instant.parse("2026-11-14T12:00:00Z")
        val id = idV7(now.minusSeconds(120))
        val issuanceRequest = issuanceRequest(id, ClientId("cliente-a"))
        val gateway = SolicitationReadFake(issuanceRequest, null)
        val useCase = RetrieveIssuanceRequestUseCase(gateway, policy(now))

        assertEquals(issuanceRequest, useCase.execute(issuanceRequest.clientId, id))
        assertFailsWith<IssuanceRequestNotFoundException> { useCase.execute(ClientId("cliente-b"), id) }
        assertEquals(0, gateway.primaryQueries)
    }

    @Test
    fun `retorna nao encontrada quando id recente nao aparece no primario`() {
        val now = Instant.parse("2026-11-14T12:00:00Z")
        val id = idV7(now.minusSeconds(2))
        val gateway = SolicitationReadFake(null, null)
        val useCase = RetrieveIssuanceRequestUseCase(gateway, policy(now))

        assertFailsWith<IssuanceRequestNotFoundException> {
            useCase.execute(ClientId("cliente"), id)
        }
        assertEquals(1, gateway.primaryQueries)
    }

    @Test
    fun `id sem formato uuid nao aciona consulta ao primario`() {
        val now = Instant.parse("2026-11-14T12:00:00Z")
        val gateway = SolicitationReadFake(null, null)
        val useCase = RetrieveIssuanceRequestUseCase(gateway, policy(now))

        assertFailsWith<IssuanceRequestNotFoundException> {
            useCase.execute(ClientId("cliente"), IssuanceRequestId("invalido"))
        }
        assertEquals(0, gateway.primaryQueries)
    }

    private fun policy(now: Instant): ConsistentReadPolicy = ConsistentReadPolicy(Duration.ofSeconds(60), Clock.fixed(now, ZoneOffset.UTC))
}

private fun issuanceRequest(
    id: IssuanceRequestId,
    client: ClientId,
): IssuanceRequestState =
    IssuanceRequestState(
        id,
        client,
        IssuanceRequestStatus.RECEBIDA,
        Instant.EPOCH,
        Instant.EPOCH,
    )

private class SolicitationReadFake(
    private val replica: IssuanceRequestState?,
    private val primary: IssuanceRequestState?,
) : IssuanceRequestReadGateway {
    var primaryQueries = 0

    override fun find(id: IssuanceRequestId): IssuanceRequestState? = replica

    override fun findConsistent(id: IssuanceRequestId): IssuanceRequestState? {
        primaryQueries++
        return primary
    }
}
