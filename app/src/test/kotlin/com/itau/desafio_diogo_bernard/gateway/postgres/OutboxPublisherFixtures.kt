@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres

import com.itau.desafio_diogo_bernard.repository.entity.OutboxEntity
import java.time.Instant
import java.util.UUID

internal const val TOPICO_TESTE = "topico.teste"
internal const val PAYLOAD_TESTE = """{"conteudo":"dado-nao-logavel"}"""

internal fun entidadeOutbox(
    id: Long,
    createdAt: Instant,
): OutboxEntity =
    OutboxEntity(
        id = id,
        eventId = UUID.randomUUID(),
        topic = TOPICO_TESTE,
        key = "chave-$id",
        payload = PAYLOAD_TESTE,
        traceparent = null,
        createdAt = createdAt,
    )

internal fun OutboxEntity.paraEvento(): OutboxEvent = OutboxEvent(id, eventId, topic, key, payload, traceparent)
