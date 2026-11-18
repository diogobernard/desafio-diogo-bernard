@file:Suppress("PackageNaming")

package com.itau.desafio_diogo_bernard.gateway.postgres

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import org.springframework.kafka.KafkaException as SpringKafkaException

@Component
class OutboxEventSender(
    private val kafka: KafkaTemplate<String, String>,
    private val openTelemetry: OpenTelemetry,
) {
    fun send(event: OutboxEvent): CompletableFuture<*> =
        try {
            parentContext(event.traceparent).makeCurrent().use { kafka.send(producerRecord(event)) }
        } catch (failure: SpringKafkaException) {
            rejectedFuture(failure)
        } catch (failure: KafkaException) {
            rejectedFuture(failure)
        } catch (failure: IllegalStateException) {
            rejectedFuture(failure)
        }

    private fun rejectedFuture(failure: Exception): CompletableFuture<*> = CompletableFuture.failedFuture<Any>(failure)

    private fun producerRecord(event: OutboxEvent): ProducerRecord<String, String> {
        val record = ProducerRecord(event.topic, event.key, event.payload)
        event.traceparent?.let { record.headers().add(TRACEPARENT, it.toByteArray()) }
        record.headers().add(SOLICITACAO_ID, event.key.toByteArray())
        return record
    }

    private fun parentContext(traceparent: String?): Context {
        if (traceparent == null) return Context.current()
        val carrier = mapOf(TRACEPARENT to traceparent)
        return openTelemetry.propagators.textMapPropagator.extract(Context.root(), carrier, GETTER)
    }

    internal companion object {
        const val TRACEPARENT = "traceparent"
        const val SOLICITACAO_ID = "solicitacao-id"
        internal val GETTER =
            object : TextMapGetter<Map<String, String>> {
                override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys

                override fun get(
                    carrier: Map<String, String>?,
                    key: String,
                ): String? = carrier?.get(key)
            }
    }
}
