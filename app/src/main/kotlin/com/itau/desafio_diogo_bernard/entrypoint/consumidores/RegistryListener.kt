package com.itau.desafio_diogo_bernard.entrypoint.consumidores

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class RegistryListener(
    private val receiver: ReceiveNotification,
    private val properties: NotaFiscalProperties,
) {
    @KafkaListener(
        topics = ["\${nota-fiscal.kafka.topico-notas}"],
        groupId = "notafiscal.registro.v1",
        containerFactory = "registryKafkaListenerContainerFactory",
    )
    fun consume(record: ConsumerRecord<String, String>): CompletableFuture<Void> =
        receiver.receive("registro", properties.consumidores.registro, record)
}
