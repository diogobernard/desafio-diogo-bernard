package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.springframework.kafka.listener.ConsumerRecordRecoverer

class KafkaConfigTest {
    @Test
    fun `cria topicos e error handler com configuracoes de retentativa`() {
        val config = KafkaConfig()
        val recoverer = ConsumerRecordRecoverer { _, _ -> }

        config.requestsTopic(NotaFiscalProperties())
        config.requestsDlqTopic(NotaFiscalProperties())
        config.invoicesTopic(NotaFiscalProperties())
        config.processingErrorHandler(NotaFiscalProperties(), recoverer)
        config.processingErrorHandler(
            NotaFiscalProperties(processamento = NotaFiscalProperties.Processamento(tentativas = 0)),
            recoverer,
        )
        config.retryListener().failedDelivery(record(), null, 1)
        config.retryListener().failedDelivery(record(), IllegalStateException("falha"), 2)
        config.retryListener().failedDelivery(record(), object : Exception() {}, 3)
    }

    private fun record(): ConsumerRecord<String, String> = ConsumerRecord("origem", 0, 1L, "chave", "valor")
}
