package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.consumerBackOff
import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import com.itau.desafio_diogo_bernard.gateway.mock.ConsumerTaskScheduler
import io.opentelemetry.context.Context
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import org.springframework.util.backoff.BackOffExecution
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException

// O retry fica no consumidor, e não no error handler do container: com listener assíncrono, o
// spring-kafka 4.1.2 só reprocessa a última de várias falhas do mesmo lote, e as demais se perdiam.
@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class ReceiveNotificationWithRetries(
    private val registerNotification: RegisterNotification,
    private val scheduler: ConsumerTaskScheduler,
    private val dlq: DlqForwarder,
) : ReceiveNotification {
    override fun receive(
        consumer: String,
        settings: Consumidor,
        record: ConsumerRecord<String, String>,
    ): CompletableFuture<Void> {
        val delivery = Delivery(consumer, settings, record)
        delivery.tryAttempt(PRIMEIRA_TENTATIVA)
        return delivery.result
    }

    private inner class Delivery(
        private val consumer: String,
        private val settings: Consumidor,
        private val record: ConsumerRecord<String, String>,
    ) {
        val result = CompletableFuture<Void>()
        private val backOff = consumerBackOff(settings).start()
        private val context = Context.current()

        fun tryAttempt(number: Int) {
            attempt().whenComplete { _, failure ->
                if (failure == null) result.complete(null) else handleFailure(failure.unwrapped(), number)
            }
        }

        // thenCompose converte uma exceção síncrona do registro em futuro com falha.
        private fun attempt(): CompletableFuture<Void> =
            CompletableFuture
                .completedFuture(record.value())
                .thenCompose { registerNotification.receive(consumer, settings, it) }

        private fun handleFailure(
            failure: Throwable,
            number: Int,
        ) {
            val delay = if (failure.isNonRetryable()) BackOffExecution.STOP else backOff.nextBackOff()
            if (delay == BackOffExecution.STOP) return forwardToDlq(failure)
            record.recordRetry(consumer, number, failure)
            schedule(Duration.ofMillis(delay)) { tryAttempt(number + 1) }
        }

        // Sem confirmação da DLQ, o offset não é liberado: a publicação é repetida até o Kafka aceitar.
        private fun forwardToDlq(failure: Throwable) {
            dlq.forward(consumer, record, failure).whenComplete { _, error ->
                if (error == null) {
                    result.complete(null)
                } else {
                    record.recordDlqFailure(consumer, error.unwrapped())
                    schedule(INTERVALO_DLQ) { forwardToDlq(failure) }
                }
            }
        }

        private fun schedule(
            delay: Duration,
            action: () -> Unit,
        ) {
            try {
                scheduler.schedule(delay, context.wrap(Runnable(action)))
            } catch (rejected: RejectedExecutionException) {
                result.completeExceptionally(rejected)
            }
        }
    }

    private companion object {
        const val PRIMEIRA_TENTATIVA = 1
        val INTERVALO_DLQ: Duration = Duration.ofSeconds(1)
    }
}
