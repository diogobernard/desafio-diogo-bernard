package com.itau.desafio_diogo_bernard.config

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.health.contributor.Status
import org.springframework.kafka.core.KafkaAdmin
import kotlin.test.assertEquals

class KafkaHealthIndicatorTest {
    @Test
    fun `informa indisponibilidade quando Kafka Admin falha`() {
        val admin = Mockito.mock(KafkaAdmin::class.java)
        Mockito.`when`(admin.clusterId()).thenThrow(IllegalStateException("falha simulada"))

        val status = KafkaHealthIndicator(admin).health().status

        assertEquals(Status.DOWN, status)
    }
}
