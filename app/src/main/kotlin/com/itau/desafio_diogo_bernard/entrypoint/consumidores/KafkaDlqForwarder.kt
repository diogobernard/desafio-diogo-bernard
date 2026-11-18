package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.BusinessMetrics
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class KafkaDlqForwarder(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val metrics: BusinessMetrics,
) : DlqForwarder {
    override fun forward(
        consumer: String,
        record: ConsumerRecord<*, *>,
        failure: Throwable,
    ): CompletableFuture<Void> =
        kafkaTemplate.send(
            "${record.topic()}.$consumer.dlq",
            record.partition(),
            record.key()?.toString(),
            record.value()?.toString(),
        ).thenAccept {
            metrics.dlq(consumer)
            record.recordSentToDlq(consumer, failure)
        }
}
