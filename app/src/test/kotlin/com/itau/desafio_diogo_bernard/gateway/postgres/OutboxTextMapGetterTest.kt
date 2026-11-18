package com.itau.desafio_diogo_bernard.gateway.postgres

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutboxTextMapGetterTest {
    @Test
    fun `expoe chaves e retorna valor quando carrier esta disponivel`() {
        val carrier = mapOf("traceparent" to "00-trace-span-01")

        assertEquals(listOf("traceparent"), OutboxEventSender.GETTER.keys(carrier).toList())
        assertEquals("00-trace-span-01", OutboxEventSender.GETTER.get(carrier, "traceparent"))
    }

    @Test
    fun `retorna null quando carrier esta ausente`() {
        assertNull(OutboxEventSender.GETTER.get(null, "traceparent"))
    }
}
