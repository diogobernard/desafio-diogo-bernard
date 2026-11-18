package com.itau.desafio_diogo_bernard.gateway.postgres

import java.util.UUID

data class OutboxEvent(
    val id: Long,
    val eventId: UUID,
    val topic: String,
    val key: String,
    val payload: String,
    val traceparent: String?,
)
