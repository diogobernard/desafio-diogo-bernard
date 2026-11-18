package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.concurrent.CompletableFuture

internal class DlqForwarderFake(
    private val failuresBeforeAccepting: Int = 0,
) : DlqForwarder {
    val forwarded = mutableListOf<Pair<String, ConsumerRecord<*, *>>>()
    private var calls = 0

    override fun forward(
        consumer: String,
        record: ConsumerRecord<*, *>,
        failure: Throwable,
    ): CompletableFuture<Void> {
        calls++
        if (calls <= failuresBeforeAccepting) {
            return CompletableFuture.failedFuture(IllegalStateException("Kafka indisponível"))
        }
        forwarded.add(consumer to record)
        return CompletableFuture.completedFuture(null)
    }
}
