package com.itau.desafio_diogo_bernard.config

import com.itau.desafio_diogo_bernard.config.properties.NotaFiscalProperties
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
@ConditionalOnExpression("'\${app.runtime.role:combined}' != 'api'")
class ConsumerTopicsConfig {
    @Bean
    fun inventoryDlqTopic(properties: NotaFiscalProperties): NewTopic = dlq(properties, CONSUMIDOR_ESTOQUE)

    @Bean
    fun registryDlqTopic(properties: NotaFiscalProperties): NewTopic = dlq(properties, CONSUMIDOR_REGISTRO)

    @Bean
    fun deliveryDlqTopic(properties: NotaFiscalProperties): NewTopic = dlq(properties, CONSUMIDOR_ENTREGA)

    @Bean
    fun financialDlqTopic(properties: NotaFiscalProperties): NewTopic = dlq(properties, CONSUMIDOR_FINANCEIRO)

    private fun dlq(
        properties: NotaFiscalProperties,
        consumer: String,
    ): NewTopic {
        val builder = TopicBuilder.name("${properties.kafka.topicoNotas}.$consumer.dlq")
        return builder.partitions(PARTICOES).replicas(properties.kafka.replicacao).build()
    }

    private companion object {
        const val PARTICOES = 10
        const val CONSUMIDOR_ESTOQUE = "estoque"
        const val CONSUMIDOR_REGISTRO = "registro"
        const val CONSUMIDOR_ENTREGA = "entrega"
        const val CONSUMIDOR_FINANCEIRO = "financeiro"
    }
}
