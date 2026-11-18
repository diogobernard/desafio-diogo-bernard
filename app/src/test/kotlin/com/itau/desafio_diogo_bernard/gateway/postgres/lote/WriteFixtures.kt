@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres.lote

import com.itau.desafio_diogo_bernard.repository.IssuanceRequestInsert
import com.itau.desafio_diogo_bernard.repository.NewOutboxEvent
import java.time.Instant
import java.util.UUID

private val INSTANTE_FIXO: Instant = Instant.parse("2026-11-16T12:00:00Z")

internal fun umaInsercao(key: UUID = UUID.randomUUID()): IssuanceRequestInsert =
    IssuanceRequestInsert(
        id = UUID.randomUUID(),
        client = "cliente-a",
        key = key,
        fingerprintValue = "a".repeat(64),
        order = "{}",
        received = INSTANTE_FIXO,
        updated = INSTANTE_FIXO,
        event = NewOutboxEvent(UUID.randomUUID(), "topico", "chave", "{}", null, INSTANTE_FIXO),
    )

internal fun umPedido(): PendingWrite = PendingWrite(umaInsercao())
