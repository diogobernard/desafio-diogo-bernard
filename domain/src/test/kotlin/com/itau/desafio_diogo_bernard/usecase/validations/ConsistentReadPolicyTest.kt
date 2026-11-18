package com.itau.desafio_diogo_bernard.usecase.validations

import com.itau.desafio_diogo_bernard.model.solicitacao.IssuanceRequestId
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsistentReadPolicyTest {
    @Test
    fun `aplica politica de leitura consistente pela idade do id`() {
        val now = Instant.parse("2026-11-14T12:00:00Z")
        val policy = ConsistentReadPolicy(Duration.ofSeconds(60), Clock.fixed(now, ZoneOffset.UTC))

        assertTrue(policy.allowsFallback(idV7(now.minusSeconds(30))))
        assertTrue(policy.allowsFallback(idV7(now.minusSeconds(60))))
        assertFalse(policy.allowsFallback(idV7(now.minusSeconds(61))))
        assertFalse(policy.allowsFallback(idV7(now.plusSeconds(1))))
        assertFalse(policy.allowsFallback(IssuanceRequestId(UUID(1L, 1L).toString())))
        val policyWithoutWindow = ConsistentReadPolicy(Duration.ZERO, Clock.systemUTC())
        assertFalse(policyWithoutWindow.allowsFallback(IssuanceRequestId("invalido")))
        assertFailsWith<IllegalArgumentException> {
            ConsistentReadPolicy(Duration.ofSeconds(-1), Clock.systemUTC())
        }
    }
}

internal fun idV7(instant: Instant): IssuanceRequestId {
    val msb = (instant.toEpochMilli() shl 16) or (7L shl 12) or 0x123L
    return IssuanceRequestId(UUID(msb, 0x8000000000000000UL.toLong()).toString())
}
