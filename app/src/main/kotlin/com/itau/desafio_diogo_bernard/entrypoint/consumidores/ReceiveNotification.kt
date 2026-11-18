package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties.Consumidor
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.concurrent.CompletableFuture

/** Conclui só depois de registrar a notificação ou de encaminhá-la à DLQ do consumidor. */
interface ReceiveNotification {
    fun receive(
        consumer: String,
        settings: Consumidor,
        record: ConsumerRecord<String, String>,
    ): CompletableFuture<Void>
}
