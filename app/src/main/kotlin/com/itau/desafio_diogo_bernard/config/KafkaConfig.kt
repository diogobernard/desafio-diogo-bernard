package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class KafkaConfig {
    @Bean
    fun requestsTopic(properties: NotaFiscalProperties): NewTopic = topic(properties.kafka.topicoSolicitacoes, properties.kafka.replicacao)

    @Bean
    fun requestsDlqTopic(properties: NotaFiscalProperties): NewTopic =
        topic(properties.kafka.topicoSolicitacoesDlq, properties.kafka.replicacao)

    @Bean
    fun invoicesTopic(properties: NotaFiscalProperties): NewTopic = topic(properties.kafka.topicoNotas, properties.kafka.replicacao)

    @Bean
    fun processingErrorHandler(
        properties: NotaFiscalProperties,
        recoverer: ConsumerRecordRecoverer,
    ): DefaultErrorHandler {
        val attempts =
            properties.processamento.tentativas
                .coerceAtLeast(1)
                .toInt()
        val backOff = ExponentialBackOffWithMaxRetries(attempts - 1)
        backOff.setInitialInterval(properties.processamento.backoff.toMillis())
        backOff.setMultiplier(FATOR_BACKOFF)
        backOff.setMaxInterval(properties.processamento.backoffMaximo.toMillis())
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(IllegalArgumentException::class.java)
            setRetryListeners(retryListener())
        }
    }

    internal fun retryListener(): RetryListener =
        RetryListener { record, error, attempt ->
            logger.warn(
                MENSAGEM_RETRY,
                record.topic(),
                record.partition(),
                record.offset(),
                attempt,
                failureName(error),
            )
        }

    private fun failureName(error: Exception?): String =
        error
            ?.let { it.javaClass.simpleName.takeIf(String::isNotEmpty) }
            ?: ERRO_DESCONHECIDO

    private fun topic(
        name: String,
        replication: Int,
    ): NewTopic =
        TopicBuilder
            .name(name)
            .partitions(PARTICOES)
            .replicas(replication)
            .build()

    private companion object {
        const val PARTICOES = 10
        const val FATOR_BACKOFF = 2.0
        const val ERRO_DESCONHECIDO = "erro desconhecido"
        const val MENSAGEM_RETRY =
            "Falha no Kafka; nova tentativa: topic={} part={} offset={} tentativa={} erro={}"
        val logger = LoggerFactory.getLogger(KafkaConfig::class.java)
    }
}
