package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.concurrent.CompletableFuture

interface DlqForwarder {
    fun forward(
        consumer: String,
        record: ConsumerRecord<*, *>,
        failure: Throwable,
    ): CompletableFuture<Void>
}
