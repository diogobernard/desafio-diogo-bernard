package com.itau.desafio_diogo_bernard.repository

import com.itau.desafio_diogo_bernard.repository.entity.OutboxEntity
import java.time.Instant
import java.util.UUID

data class NewOutboxEvent(
    val eventId: UUID,
    val topic: String,
    val key: String,
    val payload: String,
    val traceparent: String?,
    val createdAt: Instant,
) {
    fun toEntity(): OutboxEntity =
        OutboxEntity(
            eventId = eventId,
            topic = topic,
            key = key,
            payload = payload,
            traceparent = traceparent,
            createdAt = createdAt,
        )
}
