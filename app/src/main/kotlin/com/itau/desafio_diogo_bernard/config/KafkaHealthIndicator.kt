package com.itau.desafio_diogo_bernard.config

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.stereotype.Component

@Component("kafka")
class KafkaHealthIndicator(
    private val kafkaAdmin: KafkaAdmin,
) : HealthIndicator {
    override fun health(): Health =
        runCatching { kafkaAdmin.clusterId() }
            .fold(
                onSuccess = { Health.up().build() },
                onFailure = { Health.down().build() },
            )
}
