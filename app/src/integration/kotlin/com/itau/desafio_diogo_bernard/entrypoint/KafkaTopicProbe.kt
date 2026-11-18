package com.itau.desafio_diogo_bernard.entrypoint

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.UUID

internal class KafkaTopicProbe(
    private val bootstrapServers: String,
) {
    fun receberChaves(
        topic: String,
        quantity: Int,
    ): List<String> {
        val keys = mutableListOf<String>()
        KafkaConsumer<String, String>(settings()).use { consumer ->
            consumer.subscribe(listOf(topic))
            val limit = System.nanoTime() + TEMPO_LIMITE.toNanos()
            while (keys.size < quantity && System.nanoTime() < limit) {
                consumer.poll(INTERVALO_POLL).forEach { keys += it.key() }
            }
        }
        return keys
    }

    private fun settings(): Map<String, Any> =
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "topico-probe-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )

    private companion object {
        val TEMPO_LIMITE: Duration = Duration.ofSeconds(30)
        val INTERVALO_POLL: Duration = Duration.ofMillis(100)
    }
}
