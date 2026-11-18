package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ConsistentReadPolicy(
    private val window: Duration,
    private val clock: Clock,
) {
    init {
        require(!window.isNegative)
    }

    fun allowsFallback(id: IssuanceRequestId): Boolean {
        val uuid = runCatching { UUID.fromString(id.value) }.getOrNull() ?: return false
        if (uuid.version() != VERSAO_UUID_TEMPO) return false
        val createdAt = Instant.ofEpochMilli(uuid.mostSignificantBits ushr 16)
        val age = Duration.between(createdAt, clock.instant())
        return !age.isNegative && age <= window
    }

    private companion object {
        const val VERSAO_UUID_TEMPO = 7
    }
}
