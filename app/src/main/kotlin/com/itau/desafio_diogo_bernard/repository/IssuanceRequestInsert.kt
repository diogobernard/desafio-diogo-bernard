package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequest
import java.time.Instant
import java.util.UUID

data class IssuanceRequestInsert(
    val id: UUID,
    val client: String,
    val key: UUID,
    val fingerprintValue: String,
    val order: String,
    val received: Instant,
    val updated: Instant,
    val event: NewOutboxEvent,
) {
    companion object {
        fun fromDomain(
            issuanceRequest: IssuanceRequest,
            order: String,
            event: NewOutboxEvent,
        ): IssuanceRequestInsert =
            IssuanceRequestInsert(
                id = UUID.fromString(issuanceRequest.id.value),
                client = issuanceRequest.clientId.value,
                key = UUID.fromString(issuanceRequest.key.value),
                fingerprintValue = issuanceRequest.fingerprint.value,
                order = order,
                received = issuanceRequest.receivedAt,
                updated = issuanceRequest.updatedAt,
                event = event,
            )
    }
}
