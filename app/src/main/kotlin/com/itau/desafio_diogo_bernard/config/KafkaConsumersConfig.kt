package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.DefaultErrorHandler

@Configuration
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class KafkaConsumersConfig(
    private val handlers: ConsumerErrorHandlerFactory,
) {
    @Bean
    fun inventoryKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        properties: NotaFiscalProperties,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        factory(
            consumerFactory,
            handlers.create("estoque", properties.consumidores.estoque),
            properties.consumidores.estoque.maxEmVoo,
        )

    @Bean
    fun registryKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        properties: NotaFiscalProperties,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        factory(
            consumerFactory,
            handlers.create("registro", properties.consumidores.registro),
            properties.consumidores.registro.maxEmVoo,
        )

    @Bean
    fun deliveryKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        properties: NotaFiscalProperties,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        factory(
            consumerFactory,
            handlers.create("entrega", properties.consumidores.entrega),
            properties.consumidores.entrega.maxEmVoo,
        )

    @Bean
    fun financialKafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        properties: NotaFiscalProperties,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        factory(
            consumerFactory,
            handlers.create("financeiro", properties.consumidores.financeiro),
            properties.consumidores.financeiro.maxEmVoo,
        )

    // Com ack assíncrono, cada container só busca outro lote quando o atual termina. Limitar o
    // lote a max-em-voo / concorrência impede que o simulador rejeite notificações por excesso.
    // A factory não repassa kafkaConsumerProperties aos containers; por isso, o customizer.
    private fun factory(
        consumerFactory: ConsumerFactory<String, String>,
        errorHandler: DefaultErrorHandler,
        maxInFlight: Int,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val concurrency = maxInFlight.coerceAtMost(MAX_CONCORRENCIA)
        val maxBatchSize = (maxInFlight / concurrency).toString()
        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(errorHandler)
            setConcurrency(concurrency)
            containerProperties.isObservationEnabled = true
            setContainerCustomizer { container ->
                container.containerProperties.kafkaConsumerProperties
                    .setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxBatchSize)
            }
        }
    }

    private companion object {
        const val MAX_CONCORRENCIA = 10
    }
}
