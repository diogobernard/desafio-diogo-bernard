package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import com.itau.desafio_diogo_bernard.gateway.postgres.lote.IssuanceRequestWrite
import com.itau.desafio_diogo_bernard.repository.ReplicaMetricsRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ObservabilityConfig {
    @Bean
    fun pendingOutbox(
        pending: PendingOutboxCounter,
        properties: NotaFiscalProperties,
    ): MeterBinder =
        MeterBinder { registry ->
            listOf(properties.kafka.topicoSolicitacoes, properties.kafka.topicoNotas).forEach { topic ->
                Gauge
                    .builder("notafiscal.outbox.pendentes", pending) { it.pending(topic).toDouble() }
                    .tag("topico", topic)
                    .register(registry)
            }
        }

    @Bean
    fun replicaLag(
        registry: MeterRegistry,
        replica: ReplicaMetricsRepository,
    ): Gauge =
        Gauge
            .builder("notafiscal.replica.lag", replica) { it.lagSeconds() }
            .register(registry)

    @Bean
    fun ingressQueue(
        registry: MeterRegistry,
        writing: IssuanceRequestWrite,
    ): Gauge =
        Gauge
            .builder("notafiscal.ingresso.fila.tamanho", writing) { it.pending().toDouble() }
            .register(registry)
}
