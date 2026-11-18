package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.entrypoint.consumidores.DlqForwarderFake
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsumerErrorHandlerFactoryTest {
    @Test
    fun `registra retentativas com e sem causa`() {
        val listener = ConsumerErrorHandlerFactory(DlqForwarderFake()).retryListener("estoque")
        val record = ConsumerRecord<String, String>("origem", 0, 1L, "chave", "valor")

        listener.failedDelivery(record, IllegalStateException("falha"), 1)
        listener.failedDelivery(record, object : Exception() {}, 2)
        listener.failedDelivery(record, null, 2)
    }

    @Test
    fun `recoverer encaminha o registro para a DLQ do proprio consumidor`() {
        val dlq = DlqForwarderFake()
        val recoverer = ConsumerErrorHandlerFactory(dlq).recoverer("estoque")
        val record = ConsumerRecord<String, String>("origem", 0, 1L, "chave", "valor")

        recoverer.accept(record, IllegalStateException("falha"))

        assertEquals(listOf<Pair<String, ConsumerRecord<*, *>>>("estoque" to record), dlq.forwarded)
    }
}
