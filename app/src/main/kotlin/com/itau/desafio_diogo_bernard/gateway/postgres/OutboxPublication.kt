package com.itau.desafio_diogo_bernard.gateway.postgres

interface OutboxPublication {
    fun publishPending(
        topic: String,
        limit: Int,
    ): Int
}
