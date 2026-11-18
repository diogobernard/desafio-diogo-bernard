package com.itau.desafio_diogo_bernard.entrypoint

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.UUID

internal class KafkaDlqProbe(
    private val bootstrapServers: String,
) {
    fun solicitacoesRecebidas(
        topic: String,
        expectedItems: Set<String>,
    ): Set<String> {
        val receivedList = mutableSetOf<String>()
        KafkaConsumer<String, String>(settings()).use { consumer ->
            consumer.subscribe(listOf(topic))
            val limit = System.nanoTime() + TEMPO_LIMITE.toNanos()
            while (System.nanoTime() < limit && !receivedList.containsAll(expectedItems)) {
                consumer
                    .poll(INTERVALO_POLL)
                    .mapNotNullTo(receivedList) { ID_SOLICITACAO.find(it.value())?.groupValues?.get(1) }
            }
        }
        return receivedList
    }

    private fun settings(): Map<String, Any> =
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "dlq-probe-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )

    private companion object {
        val TEMPO_LIMITE: Duration = Duration.ofSeconds(30)
        val INTERVALO_POLL: Duration = Duration.ofMillis(100)
        val ID_SOLICITACAO = Regex("\"solicitacao_id\"\\s*:\\s*\"([^\"]+)")
    }
}
