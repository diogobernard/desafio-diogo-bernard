package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.mockito.Mockito
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KafkaDlqForwarderTest {
    private val registry = SimpleMeterRegistry()
    private val confirmation = CompletableFuture<SendResult<String, String>>()

    @Suppress("UNCHECKED_CAST")
    private val kafka = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
    private val dlq = KafkaDlqForwarder(kafka, BusinessMetrics(registry))

    @Test
    fun `publica na DLQ do consumidor preservando particao chave e payload`() {
        stubEnvio("chave", "valor")
        val record = ConsumerRecord<String, String>("notas", 3, 7L, "chave", "valor")

        val result = dlq.forward("estoque", record, IllegalStateException("falha"))

        assertFalse(result.isDone)
        confirmation.complete(null)
        assertTrue(result.isDone)
        Mockito.verify(kafka).send("notas.estoque.dlq", 3, "chave", "valor")
        assertEquals(1.0, contadorDlq())
    }

    @Test
    fun `aceita chave e valor ausentes e so conta depois da confirmacao`() {
        stubEnvio(null, null)
        val record = ConsumerRecord<String, String>("notas", 0, 1L, null, null)

        val result = dlq.forward("financeiro", record, IllegalStateException("falha"))
        confirmation.completeExceptionally(IllegalStateException("Kafka indisponível"))

        assertTrue(result.isCompletedExceptionally)
        assertEquals(0.0, registry.find("notafiscal.consumidor.dlq").counter()?.count() ?: 0.0)
    }

    private fun stubEnvio(
        key: String?,
        value: String?,
    ) {
        Mockito
            .`when`(kafka.send(Mockito.anyString(), Mockito.anyInt(), Mockito.eq(key), Mockito.eq(value)))
            .thenReturn(confirmation)
    }

    private fun contadorDlq(): Double =
        registry
            .get("notafiscal.consumidor.dlq")
            .tag("consumidor", "estoque")
            .counter()
            .count()
}
