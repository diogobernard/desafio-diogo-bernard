package com.itau.desafio_diogo_bernard.gateway.id

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UuidV7GeneratorTest {
    @Test
    fun `gera UUIDv7 ordenavel e extrai o instante`() {
        val instant = Instant.parse("2026-11-14T18:00:00.120Z")
        val generator = UuidV7Generator(Clock.fixed(instant, ZoneOffset.UTC))

        val firstItem = UUID.fromString(generator.generate().value)
        val secondItem = UUID.fromString(generator.generate().value)

        assertEquals(7, firstItem.version())
        assertEquals(instant.toEpochMilli(), firstItem.mostSignificantBits ushr 16)
        assertTrue(firstItem.toString() < secondItem.toString())
    }
}
