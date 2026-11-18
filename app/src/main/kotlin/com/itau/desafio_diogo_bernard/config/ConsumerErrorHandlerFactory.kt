package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.DlqForwarder
import com.itau.desafio_diogo_bernard.entrypoint.consumidores.recordRetry
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.stereotype.Component

// Contingência: o retry e a DLQ das notificações ficam em ReceberNotificacaoComRetentativas. O
// container só trata o que escapa dele, como uma falha ao publicar na própria DLQ.
@Component
class ConsumerErrorHandlerFactory(
    private val dlq: DlqForwarder,
) {
    fun create(
        consumer: String,
        settings: Consumidor,
    ): DefaultErrorHandler =
        DefaultErrorHandler(recoverer(consumer), consumerBackOff(settings)).apply {
            addNotRetryableExceptions(IllegalArgumentException::class.java)
            setRetryListeners(retryListener(consumer))
            setSeekAfterError(false)
        }

    internal fun recoverer(consumer: String): ConsumerRecordRecoverer =
        ConsumerRecordRecoverer { record, failure -> dlq.forward(consumer, record, failure).get() }

    internal fun retryListener(consumer: String): RetryListener =
        RetryListener { record, failure, attempt -> record.recordRetry(consumer, attempt, failure) }
}
