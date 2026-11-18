package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestGateway
import com.itau.desafio_diogo_bernard.gateway.IssuanceRequestRegistration
import com.itau.desafio_diogo_bernard.gateway.postgres.lote.IssuanceRequestWrite
import com.itau.desafio_diogo_bernard.model.solicitacao.ClientId
import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import com.itau.desafio_diogo_bernard.repository.IssuanceRequestRepository
import com.itau.desafio_diogo_bernard.repository.entity.StoredOrder
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Component
class IssuanceRequestPostgresGateway(
    private val requests: IssuanceRequestRepository,
    private val json: JsonMapper,
    private val outbox: OutboxEventRecorder,
    private val writing: IssuanceRequestWrite,
) : IssuanceRequestGateway {
    override fun registerOnce(issuanceRequest: IssuanceRequest): IssuanceRequestRegistration {
        val event = outbox.prepareRequest(
            UUID.fromString(issuanceRequest.id.value),
            issuanceRequest.receivedAt,
        )
        val order = json.writeValueAsString(StoredOrder.fromDomain(issuanceRequest.order))
        val insert = IssuanceRequestInsert.fromDomain(issuanceRequest, order, event)
        if (writing.persist(insert)) return IssuanceRequestRegistration.Created(issuanceRequest)
        return existing(issuanceRequest.clientId, insert.key)
    }

    private fun existing(
        client: ClientId,
        key: UUID,
    ): IssuanceRequestRegistration.Existing {
        val found = requireNotNull(requests.findByClientIdAndKey(client.value, key))
        val domainValue = found.toDomain(json)
        return IssuanceRequestRegistration.Existing(domainValue)
    }
}
